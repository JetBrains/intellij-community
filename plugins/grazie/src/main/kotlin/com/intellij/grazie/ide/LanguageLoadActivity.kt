package com.intellij.grazie.ide

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.common.KnownPhrases
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.spellchecker.SpellCheckerManager

internal class LanguageLoadActivity : ProjectActivity {
  init {
    // Do not preload proofreading in test/headless mode, so it won't slow down unrelated tests and builds.
    // We may still load it, but only when it is actually necessary.
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    GrazieSpellCheckerEngine.getInstance(project).initializeSpeller(project)
    project.serviceAsync<SpellCheckerManager>()

    GrazieSpellCheckerEngine.knownPhrases.computeIfAbsent(Language.ENGLISH) { KnownPhrases.forLanguage(Language.ENGLISH) }
      .validPhrases("Bugfix")

    val english = GrazieConfig.get().enabledLanguages.find { it.isEnglish() }
    if (english != null) {
      LangTool.getTool(english, TextStyleDomain.Other)
        .allSpellingCheckRules.forEach {
          it.isMisspelled("asdfdsfaf")
        }
    }
  }
}