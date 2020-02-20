package com.intellij.grazie.ide.inspection.detection.problem

import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.grazie.ide.inspection.detection.problem.suppress.DisableLanguageSuggestionIntention
import com.intellij.psi.PsiElement
import tanvd.grazie.langdetect.model.Language

class LanguageDetectionProblemGroup(val id: String, private val languages: Set<Language>) : SuppressableProblemGroup {
  override fun getProblemName(): String = id

  override fun getSuppressActions(element: PsiElement?) = arrayOf(
    DisableLanguageSuggestionIntention(languages)
  )
}