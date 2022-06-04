// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Adds support for author code lenses to editor.
 */
interface VcsCodeVisionLanguageContext {
  companion object {
    const val EXTENSION = "com.intellij.vcs.codeVisionLanguageContext"
    val providersExtensionPoint = LanguageExtension<VcsCodeVisionLanguageContext>(EXTENSION)
  }

  /**
   * @return true iff for particular element lens should be displayed
   */
  fun isAccepted(element: PsiElement): Boolean

  fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement)
}