// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
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
    @Property val enabledGrammarStrategies: Set<String> = defaultEnabledStrategies,
    @Property val disabledGrammarStrategies: Set<String> = HashSet(),
    @Deprecated("Moved to checkingContext in version 2") @Property val enabledCommitIntegration: Boolean = false,
    @Property val userDisabledRules: Set<String> = HashSet(),
    @Property val userEnabledRules: Set<String> = HashSet(),
    //Formerly suppressionContext -- name changed due to compatibility issues
    @Property val suppressingContext: SuppressingContext = SuppressingContext(),
    @Property val detectionContext: DetectionContext.State = DetectionContext.State(),
    @Property val checkingContext: CheckingContext = CheckingContext(),
    @Property val version: Int = 1
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
      get() = enabledLanguages.filter { it.jLanguage == null }.toSet()

    fun hasMissedLanguages(): Boolean {
      return enabledLanguages.any { it.jLanguage == null }
    }
  }

  companion object {
    private val defaultEnabledStrategies = hashSetOf("nl.rubensten.texifyidea:Latex", "org.asciidoctor.intellij.asciidoc:AsciiDoc")

    private val instance by lazy { service<GrazieConfig>() }

    const val VERSION = 2

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
    when {
      state.version == 1 -> {
        loadState(
          state.copy(
            checkingContext = CheckingContext(
              isCheckInCommitMessagesEnabled = state.enabledCommitIntegration
            ),
            version = state.version + 1
          )
        )
      }
      state.version == VERSION -> {
        val prevState = myState
        myState = state

        if (prevState != myState || prevState.availableLanguages != myState.availableLanguages) {
          service<GrazieInitializerManager>().publisher.update(prevState, myState)
        }
      }
      state.version < VERSION -> {
        loadState(state.copy(version = state.version + 1))
      }
      else -> loadState(State())
    }
  }
}
