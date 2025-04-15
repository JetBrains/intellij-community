// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.psi.reference.EditorConfigVirtualFileDescriptor
import org.editorconfig.language.services.EditorConfigFileHierarchyService
import org.editorconfig.language.services.EditorConfigServiceLoaded
import org.editorconfig.language.services.EditorConfigServiceLoading
import org.editorconfig.language.util.core.EditorConfigPsiTreeUtilCore
import kotlin.math.max

object EditorConfigPsiTreeUtil {

  inline fun <reified T : PsiElement> PsiElement.hasParentOfType(): Boolean =
    parentOfType<T>(withSelf = true) != null

  fun containsErrors(element: PsiElement): Boolean =
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
  inline fun <reified T : PsiElement> iterateTypedSiblingsForward(element: T, action: (T) -> Unit): Unit =
    element.parent.children.mapNotNull { it as? T }.dropWhile { element != it }.drop(1).forEach(action)

  /**
   * [element] is **not** included
   */
  inline fun <reified T : PsiElement> iterateTypedSiblingsBackward(element: T, action: (T) -> Unit): Unit =
    element.parent.children.mapNotNull { it as? T }.takeWhile { element != it }.reversed().forEach(action)

  fun findRemovableRange(element: PsiElement): IntRange =
    EditorConfigPsiTreeUtilCore.findRemovableRangeBackward(element)
    ?: EditorConfigPsiTreeUtilCore.findRemovableRangeForward(element)
    ?: (element.textRange.startOffset until element.textRange.endOffset)

  /**
   * current file **is** included
   */
  fun findAllParentsFiles(file: PsiFile): List<EditorConfigPsiFile> {
    val virtualFile = getOriginalFile(file)?.virtualFile ?: return emptyList()
    if (EditorConfigRegistry.shouldStopAtProjectRoot()) {
      return findParentFilesUsingIndex(file, virtualFile)
    }

    val service = EditorConfigFileHierarchyService.getInstance(file.project)
    return when (val serviceResult = service.getParentEditorConfigFiles(virtualFile)) {
      is EditorConfigServiceLoaded -> serviceResult.list
      is EditorConfigServiceLoading -> findParentFilesUsingIndex(file, virtualFile)
    }
  }

  fun findIdentifierUnderCaret(editor: Editor, file: PsiFile): PsiElement? {
    val caretOffset = editor.caretModel.offset
    val viewProvider = file.viewProvider
    val psiUnderCaret = viewProvider.findElementAt(caretOffset, EditorConfigLanguage)
    val elementType = psiUnderCaret?.node?.elementType ?: return null

    return when (elementType) {
      TokenType.WHITE_SPACE, EditorConfigElementTypes.DOT -> {
        val previousIndex = max(0, caretOffset - 1)
        val previousElement = viewProvider.findElementAt(previousIndex)
        if (previousElement?.node?.elementType != EditorConfigElementTypes.IDENTIFIER) null
        else previousElement
      }

      EditorConfigElementTypes.IDENTIFIER -> psiUnderCaret

      else -> null
    }
  }

  private fun findParentFilesUsingIndex(file: PsiFile, virtualFile: VirtualFile): List<EditorConfigPsiFile> {
    val currentFileDescriptor = EditorConfigVirtualFileDescriptor(virtualFile)
    val project = file.project

    val sortedParentFiles = EditorConfigVfsUtil.getEditorConfigFiles(project)
      .asSequence()
      .filter(currentFileDescriptor::isChildOf)
      .sortedBy(currentFileDescriptor::distanceToParent)

    val manager = PsiManager.getInstance(project)
    val firstRoot = sortedParentFiles.indexOfFirst {
      val psiFile = manager.findFile(it) as? EditorConfigPsiFile
      psiFile?.hasValidRootDeclaration ?: false
    }

    val actualParentFiles =
      if (firstRoot < 0) sortedParentFiles
      else sortedParentFiles.take(firstRoot + 1)

    return actualParentFiles.mapNotNull {
      manager.findFile(it) as? EditorConfigPsiFile
    }.toList()
  }

  /**
   * current file is **not** included
   */
  fun findAllChildrenFiles(file: EditorConfigPsiFile, honorRoot: Boolean = true): List<EditorConfigPsiFile> {
    val virtualFile = getOriginalFile(file)?.virtualFile ?: return emptyList()
    val project = file.project

    val manager = PsiManager.getInstance(project)

    val childFiles = EditorConfigVfsUtil.getEditorConfigFiles(project)
      .asSequence()
      .filter(EditorConfigVirtualFileDescriptor(virtualFile)::isStrictParentOf)
      .associateBy { manager.findFile(it) as? EditorConfigPsiFile }
      .filterKeys { it != null }

    if (honorRoot) return childFiles.keys.filterNotNull()

    return childFiles.filter { (_, virtualChildFile) ->
      // keep the .editorconfig if none of its ancestors (and itself) contain 'root = true'
      childFiles.all { (otherChildFile, virtualOtherChildFile) ->
        when {
          !VfsUtil.isAncestor(virtualOtherChildFile, virtualChildFile, false) -> true
          otherChildFile!!.hasValidRootDeclaration -> false
          else -> true
        }
      }
    }.keys.filterNotNull()
  }

  fun findOneParentFile(file: EditorConfigPsiFile): EditorConfigPsiFile? {
    if (file.hasValidRootDeclaration) return null
    return findNextEditorConfigFile(file.parent?.parentDirectory)
  }

  private tailrec fun findNextEditorConfigFile(directory: PsiDirectory?): EditorConfigPsiFile? {
    directory ?: return null
    val result = directory.files.firstOrNull { it is EditorConfigPsiFile } as? EditorConfigPsiFile
    return result ?: findNextEditorConfigFile(directory.parentDirectory)
  }

  fun findIdentifierUnderCaret(element: PsiElement?): PsiElement? {
    element ?: return null
    val project = element.project
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor(true) ?: return null
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

  inline fun <reified T : PsiFile> getOriginalFile(file: T?): T? =
    EditorConfigPsiTreeUtilCore.getOriginalFile(file, T::class)
}
