// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.htmltools.ide

import com.intellij.codeInsight.editorActions.XmlEditUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.htmltools.HtmlToolsBundle
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlChildRole
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.intellij.images.index.ImageInfoIndex
import org.intellij.images.util.ImageInfo
import java.io.File
import java.text.MessageFormat

internal class HtmlFileDropHandler : FileDropHandler {
  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    val editor = e.editor ?: return false

    val target = readAction {
      val target = PsiDocumentManager.getInstance(e.project).getPsiFile(editor.document)
      target as? HtmlFileImpl
    } ?: return false

    val file = getDroppedFile(e.files) ?: return false

    when {
      isCSS(file.fileType) -> {
        insertTagInHead(target, file, editor, "<link rel={0}stylesheet{0} href={0}{1}{0}>")
        return true
      }
      isJS(file.fileType) -> {
        insertTagInHead(target, file, editor, "<script src={0}{1}{0}></script>")
        return true
      }
      isImage(file.fileType) -> {
        insertImageTag(target, file, editor, "<img src={0}{1}{0}/>")
        return true
      }
      else -> return false
    }
  }
}

private suspend fun insertTagInHead(target: HtmlFileImpl, droppedFile: VirtualFile, editor: Editor, tagTemplate: String) {
  val (relativePath, parent, existingPosition, tagText) = readAction {
    val relativePath = getRelativePath(target, droppedFile)
    val tagText = prepareTagText(target, tagTemplate, relativePath)
    val document = target.document
    if (document == null || tagText == null) return@readAction null

    val topLevelTags = PsiTreeUtil.getChildrenOfType(document, XmlTag::class.java)
    var html: XmlTag? = null
    var head: XmlTag? = null
    if (topLevelTags != null) {
      for (tag in topLevelTags) {
        if ("html" == tag.name) {
          html = tag
          break
        }
        if ("head" == tag.name) {
          head = tag
          break
        }
      }
    }

    if (html != null) {
      head = html.findFirstSubTag("head")
    }
    val parent: PsiElement = head ?: (html ?: document)

    val existingPosition = findExistingPosition(editor, tagTemplate, relativePath, parent)
    InsertTagData(relativePath, parent, existingPosition, tagText)
  } ?: return

  withContext(Dispatchers.EDT) {
    if (editor.isDisposed) return@withContext

    if (existingPosition != null) {
      editor.scrollingModel.scrollTo(existingPosition, ScrollType.MAKE_VISIBLE)
      val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING
      val hint = LightweightHint(HintUtil.createInformationLabel(HtmlToolsBundle.message("html.drop.handler.hint.text.file.already.linked", relativePath)))
      val point = HintManagerImpl.getHintPosition(hint, editor, existingPosition, HintManager.ABOVE)
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, 0, false)
    }
    else {
      WriteCommandAction.writeCommandAction(target.project, target).run<RuntimeException> {
        val tag = XmlElementFactory.getInstance(target.project).createHTMLTagFromText(tagText)
        if (parent is XmlTag) {
          parent.addSubTag(tag, "head" != parent.name)
        }
        else {
          parent.addAfter(tag, (parent as XmlDocument).prolog)
        }
      }
    }
  }
}

private data class InsertTagData(val relativePath: String?,
                                 val parent: PsiElement,
                                 val existingPosition: LogicalPosition?,
                                 val tagText: String)

@RequiresReadLock
private fun findExistingPosition(editor: Editor, tagTemplate: String, relativePath: String?, parent: PsiElement): LogicalPosition? {
  for (element in parent.children) {
    if (element is XmlTag) {
      if (tagTemplate.startsWith("<" + element.name + " ")) {
        val attrName = if ((HtmlUtil.SCRIPT_TAG_NAME == element.name)) "src" else "href"
        val path = element.getAttributeValue(attrName)
        if (StringUtil.equals(path, relativePath)) {
          val position = getPosition(editor, element, attrName)
          return position
        }
      }
    }
  }
  return null
}

private fun getPosition(editor: Editor, tag: XmlTag, attrName: String): LogicalPosition {
  val element = tag.getAttribute(attrName)!!.valueElement
  return editor.offsetToLogicalPosition(element!!.textRange.startOffset + element.textRange.length / 2)
}

private suspend fun insertImageTag(target: HtmlFileImpl, droppedFile: VirtualFile, editor: Editor, tagTemplate: String) {
  val (parent, insertionPoint, tagText, imageInfo) = readAction {
    val tagText = prepareTagText(target, tagTemplate, getRelativePath(target, droppedFile))
    val offset = editor.caretModel.offset
    val element = target.findElementAt(offset)
    if (element == null || tagText == null) return@readAction null

    val parent: XmlElement = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, XmlDocument::class.java) ?: return@readAction null
    var candidate: PsiElement? = null
    for (psiElement in parent.children) {
      if (psiElement.textRange.endOffset >= offset) {
        candidate = psiElement
        break
      }
    }
    if (parent is XmlTag && candidate != null) {
      val startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(parent.getNode())
      if (startEnd != null && startEnd.textRange.endOffset > candidate.textRange.endOffset) {
        candidate = startEnd.psi
      }
      val endStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parent.getNode())
      if (endStart != null && endStart.textRange.startOffset <= candidate!!.textRange.startOffset) {
        candidate = endStart.psi.prevSibling
      }
    }
    val insertionPoint = candidate
    val imageInfo = ImageInfoIndex.getInfo(droppedFile, target.getProject())

    InsertImageTagData(parent, insertionPoint, tagText, imageInfo)
  } ?: return

  withContext(Dispatchers.EDT) {
    WriteCommandAction.writeCommandAction(target.project, target).run<RuntimeException> {
      if (!parent.isValid) return@run

      var tag = XmlElementFactory.getInstance(target.project).createHTMLTagFromText(tagText)
      tag = parent.addAfter(tag, insertionPoint) as XmlTag
      if (imageInfo != null && imageInfo.width != 0 && imageInfo.height != 0) {
        tag.setAttribute("height", imageInfo.height.toString())
        tag.setAttribute("width", imageInfo.width.toString())
      }
    }
  }
}

data class InsertImageTagData(val parent: XmlElement,
                              val insertionPoint: PsiElement?,
                              val tagText: String,
                              val imageInfo: ImageInfo?)

private fun prepareTagText(target: HtmlFileImpl, tagTemplate: String, path: String?): String? {
  return if (path != null) MessageFormat.format(tagTemplate, XmlEditUtil.getAttributeQuote(target), path) else null
}

private fun getRelativePath(target: HtmlFileImpl, droppedFile: VirtualFile): String? {
  val targetFile = target.virtualFile
  return FileUtil.getRelativePath(targetFile.parent.path, droppedFile.path, '/')
}

private fun getDroppedFile(list: Collection<File>): VirtualFile? {
  val io = if (list.size == 1) list.firstOrNull() else null
  return if (io != null) VfsUtil.findFileByIoFile(io, true) else null
}

private fun isJS(type: FileType): Boolean {
  return type is LanguageFileType && type.language.isKindOf(Language.findLanguageByID("JavaScript"))
}

private fun isCSS(type: FileType): Boolean {
  return type is LanguageFileType && type.language.isKindOf(Language.findLanguageByID("CSS"))
}

private fun isImage(type: FileType): Boolean {
  return type === ImageFileTypeManager.getInstance().imageFileType
}