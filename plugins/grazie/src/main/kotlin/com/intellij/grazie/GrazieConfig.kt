// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.detection.DetectionContext
import com.intellij.grazie.grammar.suppress.SuppressionContext
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.orTrue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property

@State(name = "GraziConfig", storages = [
  Storage(value = "grazi_global.xml", deprecated = true),
  Storage("grazie_global.xml")
])
class GrazieConfig : PersistentStateComponent<GrazieConfig.State> {
  /**
   * State of Grazie plugin
   *
   * Note, that all serialized values should be MUTABLE.
   * Immutable values (like emptySet()) as default may lead to deserialization failure
   */
  data class State(
    @Property val enabledLanguages: Set<Lang> = hashSetOf(Lang.AMERICAN_ENGLISH),
    @Property val nativeLanguage: Lang = enabledLanguages.first(),
    @Property val enabledProgrammingLanguages: Set<String> = defaultEnabledProgrammingLanguages,
    @Property val enabledGrammarStrategies: Set<String> = defaultEnabledStrategies,
    @Property val disabledGrammarStrategies: Set<String> = HashSet(),
    @Property val enabledCommitIntegration: Boolean = false,
    @Property val userDisabledRules: Set<String> = HashSet(),
    @Property val userEnabledRules: Set<String> = HashSet(),
    @Property val suppressionContext: SuppressionContext = SuppressionContext(),
    @Property val detectionContext: DetectionContext.State = DetectionContext.State(),
    @Property val version: Int = 0
  ) {
    /**
     * Available languages set depends on current loaded LanguageTool modules.
     *
     * Note, that after loading of new module this field will not change. It will
     * remain equal to the moment field was accessed first time.
     *
     * Lazy is used, because deserialized properties are updated during initial deserialization
     *
     * *NOTE: By default availableLanguages are not included into equals. Check for it manually.*
     */
    val availableLanguages: Set<Lang> by lazy { enabledLanguages.filter { it.jLanguage != null }.toSet() }

    val missedLanguages: Set<Lang>
      get() = enabledLanguages.filter { it.jLanguage == null }.toSet() +
              setOf(nativeLanguage).takeIf { nativeLanguage.jLanguage == null }.orEmpty()

    fun hasMissedLanguages(withNative: Boolean = true): Boolean {
      return (withNative && nativeLanguage.jLanguage == null) || enabledLanguages.any { it.jLanguage == null }
    }
  }

  companion object {
    private val defaultEnabledStrategies = hashSetOf("nl.rubensten.texifyidea:Latex", "org.asciidoctor.intellij.asciidoc:AsciiDoc")
    private val defaultEnabledProgrammingLanguages by lazy {
      when {
        GraziePlugin.isBundled && ApplicationManager.getApplication()?.isUnitTestMode?.not().orTrue() -> {
          hashSetOf("AsciiDoc", "Latex", "Markdown")
        }
        else -> hashSetOf(
          "AsciiDoc", "Latex", "Markdown",
          "JAVA",
          "JavaScript", "JavaScript 1.5", "JavaScript 1.8",
          "JSX Harmony", "ECMAScript 6",
          "JSON", "JSON5", "HTML", "XML", "yaml",
          "Python",
          "Properties", "TEXT",
          "go", "Rust"
        )
      }
    }

    private val instance: GrazieConfig by lazy { service<GrazieConfig>() }

    const val VERSION = 1

    /**
     * Get copy of Grazie config state
     *
     * Should never be called in GrazieStateLifecycle actions
     */
    fun get() = instance.state

    /** Update Grazie config state */
    @Synchronized
    fun update(change: (State) -> State) = instance.loadState(change(get()))
  }

  private var myState = State()

  override fun getState() = myState

  override fun loadState(state: State) {
    when (state.version) {
      0 -> {
        val enabledStrategies = state.enabledGrammarStrategies.toMutableSet()
        val disabledStrategies = state.disabledGrammarStrategies.toMutableSet()

        LanguageGrammarChecking.getLanguageExtensionPoints().forEach {
          val instance = it.instance

          if (it.language in defaultEnabledProgrammingLanguages) {
            if (it.language !in state.enabledProgrammingLanguages) {
              disabledStrategies.add(instance.getID())
              enabledStrategies.remove(instance.getID())
            }
          }
          else {
            if (it.language in state.enabledProgrammingLanguages) {
              enabledStrategies.add(instance.getID())
              disabledStrategies.remove(instance.getID())
            }
          }
        }

        loadState(state.copy(enabledProgrammingLanguages = HashSet(),
                             enabledGrammarStrategies = enabledStrategies,
                             disabledGrammarStrategies = disabledStrategies,
                             version = state.version + 1))
      }
      VERSION -> {
        val prevState = myState
        myState = state

        if (prevState != myState || prevState.availableLanguages != myState.availableLanguages) {
          service<GrazieInitializerManager>().publisher.update(prevState, myState)
        }
      }
      else -> error("Unknown version of Grazie settings")
    }
  }
}
