package com.intellij.grazie.ide.inspection.detection.problem

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.detection.toLangOrNull
import com.intellij.grazie.ide.inspection.detection.quickfix.DownloadLanguageQuickFix
import com.intellij.grazie.ide.inspection.detection.quickfix.GrazieGoToSettingsQuickFix
import com.intellij.grazie.ide.inspection.detection.quickfix.NeverSuggestLanguageQuickFix
import com.intellij.grazie.utils.toPointer
import com.intellij.psi.PsiFile


object LanguageDetectionProblemDescriptor {
  fun create(
    manager: InspectionManager,
    isOnTheFly: Boolean,
    file: PsiFile,
    languages: Set<Language>
  ): ProblemDescriptor? {
    val langs = languages.mapNotNull { it.toLangOrNull() }
    if (langs.isEmpty()) {
      return null
    }

    val text = when {
      langs.size in 1..3 && langs.all { it.isAvailable() } -> {
        GrazieBundle.message("grazie.detection.problem.enable.several.text", languages.joinToString { it.englishName })
      }
      langs.size in 1..3 && langs.any { !it.isAvailable() } -> {
        GrazieBundle.message("grazie.detection.problem.download.several.text", languages.joinToString { it.englishName })
      }
      langs.size > 3 && langs.all { it.isAvailable() } -> GrazieBundle.message("grazie.detection.problem.enable.many.text")
      langs.size > 3 && langs.any { !it.isAvailable() } -> GrazieBundle.message("grazie.detection.problem.download.many.text")
      else -> error("Unexpected text during create of language detection problem descriptor")
    }

    val fixes = when (langs.size) {
      1 -> arrayOf(DownloadLanguageQuickFix(languages), NeverSuggestLanguageQuickFix(file.toPointer(), languages))
      2, 3 -> arrayOf(DownloadLanguageQuickFix(languages), GrazieGoToSettingsQuickFix(),
                      NeverSuggestLanguageQuickFix(file.toPointer(), languages))
      else -> arrayOf(GrazieGoToSettingsQuickFix(), NeverSuggestLanguageQuickFix(file.toPointer(), languages))
    }

    return manager.createProblemDescriptor(file, text, isOnTheFly, fixes, ProblemHighlightType.WEAK_WARNING)
  }
}
