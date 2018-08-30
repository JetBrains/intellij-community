// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.core.EditorConfigPsiTreeUtilCore
import kotlin.math.max

object EditorConfigPsiTreeUtil {
  inline fun <reified T : PsiElement> PsiElement.getParentOfType(strict: Boolean = false) =
    PsiTreeUtil.getParentOfType(this, T::class.java, strict)

  inline fun <reified T : PsiElement> PsiElement.hasParentOfType() =
    getParentOfType<T>() != null

  inline fun <reified T : PsiElement> getRequiredParent(element: PsiElement): T =
    PsiTreeUtil.getParentOfType(element, T::class.java, false) ?: throw IllegalStateException()

  fun containsErrors(element: PsiElement) =
    SyntaxTraverser.psiTraverser(element).traverse().filter(PsiErrorElement::class.java).isNotEmpty

  fun findShadowingSections(section: EditorConfigSection): List<EditorConfigSection> {
    val result = mutableListOf(section)
    iterateTypedSiblingsForward(section) {
      if (section.header.isSubcaseOf(it.header)) {
        result.add(it)
      }
    }

    return result
  }

  fun findShadowedSections(section: EditorConfigSection): List<EditorConfigSection> {
    val result = mutableListOf(section)
    iterateTypedSiblingsBackward(section) {
      if (it.header.isSubcaseOf(section.header)) {
        result.add(it)
      }
    }
    result.reverse()
    return result
  }

  /**
   * [element] is **not** included
   */
  inline fun <reified T : PsiElement> iterateTypedSiblingsForward(element: T, action: (T) -> Unit) =
    element.parent.children.mapNotNull { it as? T }.dropWhile { element != it }.drop(1).forEach(action)

  /**
   * [element] is **not** included
   */
  inline fun <reified T : PsiElement> iterateTypedSiblingsBackward(element: T, action: (T) -> Unit) =
    element.parent.children.mapNotNull { it as? T }.takeWhile { element != it }.reversed().forEach(action)

  fun findRemovableRange(element: PsiElement): IntRange =
    EditorConfigPsiTreeUtilCore.findRemovableRangeBackward(element)
    ?: EditorConfigPsiTreeUtilCore.findRemovableRangeForward(element)
    ?: element.textRange.startOffset until element.textRange.endOffset

  fun findIdentifierUnderCaret(editor: Editor, file: PsiFile): PsiElement? {
    val caretOffset = editor.caretModel.offset
    val viewProvider = file.viewProvider
    val psiUnderCaret = viewProvider.findElementAt(caretOffset, EditorConfigLanguage)

    return when (psiUnderCaret?.node?.elementType) {
      EditorConfigElementTypes.IDENTIFIER -> psiUnderCaret

      null, TokenType.WHITE_SPACE, EditorConfigElementTypes.DOT -> {
        val previousIndex = max(0, caretOffset - 1)
        val previousElement = viewProvider.findElementAt(previousIndex)
        if (previousElement?.node?.elementType != EditorConfigElementTypes.IDENTIFIER) null
        else previousElement
      }

      else -> null
    }
  }

  /**
   * current file **is** included
   */
  fun findAllParentsFiles(file: PsiFile, honorRoot: Boolean = true): List<EditorConfigPsiFile> {
    val name = EditorConfigFileConstants.FILE_NAME
    val result = mutableListOf<EditorConfigPsiFile>()

    fun handle(directory: PsiDirectory) {
      val child = directory.findFile(name)
      if (child is EditorConfigPsiFile) {
        result.add(child)
        if (honorRoot && child.hasValidRootDeclaration) return
      }

      directory.parent?.apply(::handle)
    }

    getOriginalFile(file)?.parent?.apply(::handle)
    return result
  }

  /**
   * current file is **not** included
   */
  fun findAllChildrenFiles(file: EditorConfigPsiFile, honorRoot: Boolean = true): List<EditorConfigPsiFile> {
    val name = EditorConfigFileConstants.FILE_NAME
    val result = mutableListOf<EditorConfigPsiFile>()

    fun handle(directory: PsiDirectory) {
      val child = directory.findFile(name) as? EditorConfigPsiFile
      child?.let {
        if (honorRoot && it.hasValidRootDeclaration) return
        result.add(it)
      }

      directory.subdirectories.forEach(::handle)
    }

    file.parent?.subdirectories?.forEach(::handle)
    return result
  }

  fun findOneParentFile(file: EditorConfigPsiFile): EditorConfigPsiFile? {
    if (file.hasValidRootDeclaration) return null
    return findNextEditorCOnfigFile(file.parent?.parentDirectory)
  }

  private tailrec fun findNextEditorCOnfigFile(directory: PsiDirectory?): EditorConfigPsiFile? {
    directory ?: return null
    val result = directory.files.firstOrNull { it is EditorConfigPsiFile } as? EditorConfigPsiFile
    return result ?: findNextEditorCOnfigFile(directory.parentDirectory)
  }

  fun findIdentifierUnderCaret(element: PsiElement?): PsiElement? {
    element ?: return null
    val project = element.project
    val editor = (FileEditorManager.getInstance(project) as? FileEditorManagerImpl)?.getSelectedTextEditor(true) ?: return null
    val file = element.containingFile ?: return null
    return findIdentifierUnderCaret(editor, file)
  }

  tailrec fun nextVisibleSibling(element: PsiElement): PsiElement? {
    val next = element.nextSibling ?: return null
    if (next !is PsiWhiteSpace) return next
    return nextVisibleSibling(next)
  }

  inline fun iterateVisibleChildren(element: PsiElement, acceptor: (PsiElement) -> Unit) {
    var child: PsiElement? = element.firstChild
    while (child != null) {
      acceptor(child)
      child = nextVisibleSibling(child)
    }
  }

  inline fun <reified T : PsiFile> getOriginalFile(file: T?) =
    EditorConfigPsiTreeUtilCore.getOriginalFile(file, T::class)
}
