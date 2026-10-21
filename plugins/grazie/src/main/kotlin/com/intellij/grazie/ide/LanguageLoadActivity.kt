package com.intellij.grazie.ide

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.common.KnownPhrases
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.jlanguage.LazyCachingConcurrentDisambiguator
import com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.spellchecker.SpellCheckerManager

internal abstract class LanguageLoadActivity : ProjectActivity {
  init {
    // Do not preload proofreading in test/headless mode, so it won't slow down unrelated tests and builds.
    // We may still load it, but only when it is actually necessary.
    if (ApplicationManager.getApplication().isHeadlessEnvironment || IdeProductMode.isLight) {
      throw ExtensionNotApplicableException.create()
    }
  }

  protected fun findEnglish(): Lang? = GrazieConfig.get().enabledLanguages.find { it.isEnglish() }

  internal class SpellingPreloader : LanguageLoadActivity() {
    override suspend fun execute(project: Project) {
      GrazieSpellCheckerEngine.getInstance(project).ensureSpellerInitialized()
      SpellCheckerManager.getInstance(project)
    }
  }

  internal class KnownPhrasePreloader : LanguageLoadActivity() {
    override suspend fun execute(project: Project) {
      GrazieSpellCheckerEngine.knownPhrases.computeIfAbsent(Language.ENGLISH) { KnownPhrases.forLanguage(Language.ENGLISH) }
        .validPhrases("Bugfix")
    }
  }

  internal class LanguageToolPreloader : LanguageLoadActivity() {
    override suspend fun execute(project: Project) {
      val english = findEnglish() ?: return
      LangTool.getTool(english, TextStyleDomain.Other)
        .allSpellingCheckRules.forEach {
          it.isMisspelled("asdfdsfaf")
        }
    }

  }

  internal class DisambiguatorPreloader : LanguageLoadActivity() {
    override suspend fun execute(project: Project) {
      (findEnglish()?.jLanguage?.disambiguator as? LazyCachingConcurrentDisambiguator)?.ensureInitialized()
    }
  }
}