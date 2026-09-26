// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.inspection.detection.quickfix

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toLang
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.openapi.project.Project

class DownloadLanguageQuickFix(private val languages: Set<Language>) : LocalQuickFix {
  private val langs = languages.map { it.toLang() }

  override fun getFamilyName() = GrazieBundle.message("grazie.detection.quickfix.download.family")

  override fun getName() = when {
    langs.size in 1..3 && langs.all { it.isAvailable() } -> {
      GrazieBundle.message("grazie.detection.quickfix.enable.several.text", languages.joinToString { it.englishName })
    }
    langs.size in 1..3 && langs.any { !it.isAvailable() } -> {
      GrazieBundle.message("grazie.detection.quickfix.download.several.text", languages.joinToString { it.englishName })
    }
    langs.size > 3 && langs.all { it.isAvailable() } -> GrazieBundle.message("grazie.detection.quickfix.enable.many.text")
    langs.size > 3 && langs.any { !it.isAvailable() } -> GrazieBundle.message("grazie.detection.quickfix.download.many.text")
    else -> error("Unexpected situation during definition of name for download language quick fix")
  }

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    GrazieRemote.downloadAsync(langs, project)
    GrazieConfig.update { state -> state.copy(enabledLanguages = state.enabledLanguages + langs) }
    GrazieFUSCounter.languagesSuggested(languages, isEnabled = true)
  }
}
