package com.intellij.grazie.ide.inspection.detection.problem

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.detection.displayName
import com.intellij.grazie.detection.toLang
import com.intellij.grazie.ide.inspection.detection.quickfix.DownloadLanguageQuickFix
import com.intellij.grazie.ide.inspection.detection.quickfix.GrazieGoToSettingsQuickFix
import com.intellij.psi.PsiFile
import tanvd.grazie.langdetect.model.Language


object LanguageDetectionProblemDescriptor {
  fun create(id: String, manager: InspectionManager, isOnTheFly: Boolean, file: PsiFile, languages: Set<Language>): ProblemDescriptor {
    val langs = languages.map { it.toLang() }

    val text = when {
      langs.size in 1..3 && langs.all { it.isAvailable() } -> {
        GrazieBundle.message("grazie.detection.problem.enable.several.text", languages.joinToString { it.displayName })
      }
      langs.size in 1..3 && langs.any { !it.isAvailable() } -> {
        GrazieBundle.message("grazie.detection.problem.download.several.text", languages.joinToString { it.displayName })
      }
      langs.size > 3 && langs.all { it.isAvailable() } -> GrazieBundle.message("grazie.detection.problem.enable.many.text")
      langs.size > 3 && langs.any { !it.isAvailable() } -> GrazieBundle.message("grazie.detection.problem.download.many.text")
      else -> error("Unexpected text during create of language detection problem descriptor")
    }

    val fixes = when (langs.size) {
      1 -> arrayOf(DownloadLanguageQuickFix(languages))
      2, 3 -> arrayOf(DownloadLanguageQuickFix(languages), GrazieGoToSettingsQuickFix())
      else -> arrayOf(GrazieGoToSettingsQuickFix())
    }

    return manager.createProblemDescriptor(file, text, isOnTheFly, fixes, ProblemHighlightType.WARNING).also {
      it.problemGroup = LanguageDetectionProblemGroup(id, languages)
    }
  }
}
