// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent

/**
 * Adds support for author code lenses to editor.
 */
@ApiStatus.Experimental
interface VcsCodeVisionLanguageContext {
  companion object {
    const val EXTENSION: String = "com.intellij.vcs.codeVisionLanguageContext"
    val providersExtensionPoint: LanguageExtension<VcsCodeVisionLanguageContext> = LanguageExtension<VcsCodeVisionLanguageContext>(EXTENSION)
  }

  /**
   * @return true iff for particular element lens should be displayed
   */
  fun isAccepted(element: PsiElement): Boolean

  fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement)

  /**
   * When file's language differs from the language of this extension, this method tells in which files elements still need to be searched.
   * It is done for optimization to avoid walking the whole file if it's known that it doesn't contain supported languages.
   */
  @ApiStatus.Experimental
  fun isCustomFileAccepted(file: PsiFile): Boolean = false

  /**
   * This method returns a text range that will be considered when computing the code author
   * for a given element. The method is necessary for the correct display of new elements. An
   * inlay hint "new *" will be displayed next to an element iff all lines in the element text
   * range are new according to VCS. If you have deleted a method and written a new method in
   * the same place, but both the deleted and new methods have the same annotation line on the
   * same line, it is recommended to consider a text range without this annotation to display
   * this method as new, but not as written by the author or the line with the annotation.
   * */
  fun computeEffectiveRange(element: PsiElement): TextRange {
    val start = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: element
    return TextRange.create(start.startOffset, element.endOffset)
  }
}