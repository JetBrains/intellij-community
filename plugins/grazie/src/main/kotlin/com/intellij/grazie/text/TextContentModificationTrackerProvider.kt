package com.intellij.grazie.text

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement

interface TextContentModificationTrackerProvider {
  companion object {
    @JvmField
    val EP_NAME: LanguageExtension<TextContentModificationTrackerProvider> = LanguageExtension("com.intellij.grazie.textContentModificationTrackerProvider")
  }

  fun getModificationTracker(psiElement: PsiElement): ModificationTracker?
}