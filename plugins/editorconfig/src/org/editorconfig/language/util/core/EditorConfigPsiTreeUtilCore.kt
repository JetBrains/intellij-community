// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util.core

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.EditorConfigSection
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.isSubcaseOf
import kotlin.reflect.KClass
import kotlin.reflect.full.safeCast

object EditorConfigPsiTreeUtilCore {
  fun findMatchingSections(section: EditorConfigSection) =
    findMatchingParentSections(section) + findMatchingChildSections(section)

  private fun findMatchingParentSections(section: EditorConfigSection): List<EditorConfigSection> {
    val psiFile = section.containingFile as? EditorConfigPsiFile ?: return emptyList()
    return EditorConfigPsiTreeUtil.findAllParentsFiles(psiFile)
      .flatMap(EditorConfigPsiFile::sections)
      .filter { section.header.isSubcaseOf(it.header) }
  }

  private fun findMatchingChildSections(section: EditorConfigSection): List<EditorConfigSection> {
    val psiFile = section.containingFile as? EditorConfigPsiFile ?: return emptyList()
    return EditorConfigPsiTreeUtil.findAllChildrenFiles(psiFile)
      .flatMap(EditorConfigPsiFile::sections)
      .filter { it.header.isSubcaseOf(section.header) }
  }

  fun findRemovableRangeBackward(pattern: PsiElement): IntRange? {
    val prevElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(pattern) ?: return null
    if (prevElement.node.elementType != EditorConfigElementTypes.COMMA) {
      return null
    }

    val prevPrevElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(prevElement)
    if (prevPrevElement == null) {
      val start = prevElement.textRange.startOffset
      val end = pattern.textRange.endOffset
      return start until end
    }

    val start = prevPrevElement.textRange.endOffset
    val end = pattern.textRange.endOffset
    return start until end
  }

  fun findRemovableRangeForward(pattern: PsiElement): IntRange? {
    val nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(pattern) ?: return null
    if (nextElement.node?.elementType != EditorConfigElementTypes.COMMA) {
      return null
    }

    val nextNextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextElement)
    if (nextNextElement == null) {
      val start = pattern.textRange.startOffset
      val end = nextElement.textRange.endOffset
      return start until end
    }

    val start = pattern.textRange.startOffset
    val end = nextNextElement.textRange.startOffset
    return start until end
  }

  tailrec fun <T : PsiFile> getOriginalFile(file: T?, cls: KClass<T>): T? {
    val originalFile = cls.safeCast(file?.originalFile)
    if (file == originalFile) return file
    else return getOriginalFile(originalFile, cls)
  }
}
