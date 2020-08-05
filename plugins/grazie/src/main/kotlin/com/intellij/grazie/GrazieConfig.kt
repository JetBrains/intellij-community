// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.config.migration.VersionedState
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Property
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet

@State(name = "GraziConfig", storages = [
  Storage("grazie_global.xml"),
  Storage(value = "grazi_global.xml", deprecated = true)
])
class GrazieConfig : PersistentStateComponent<GrazieConfig.State> {
  @Suppress("unused")
  enum class Version : VersionedState.Version<State> {
    INITIAL,

    //Since commit abc7e5f5
    OLD_UI {
      override fun migrate(state: State) = state.copy(
        checkingContext = CheckingContext(
          isCheckInCommitMessagesEnabled = state.enabledCommitIntegration
        )
      )
    },

    //Since commit cc47dd17
    NEW_UI;

    override fun next() = values().getOrNull(ordinal + 1)
    override fun toString() = ordinal.toString()

    companion object {
      val CURRENT = NEW_UI
    }
  }

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
    @Property override val version: Version = Version.CURRENT
  ) : VersionedState<Version, State> {
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
    val availableLanguages: Set<Lang> by lazy {
      enabledLanguages.asSequence().filter { it.jLanguage != null }.toCollection(ObjectLinkedOpenHashSet())
    }

    val missedLanguages: Set<Lang>
      get() = enabledLanguages.asSequence().filter { it.jLanguage == null }.toCollection(ObjectLinkedOpenHashSet())

    override fun increment() = copy(version = version.next() ?: error("Attempt to increment latest version $version"))

    fun hasMissedLanguages(): Boolean {
      return enabledLanguages.any { it.jLanguage == null }
    }
  }

  companion object {
    private val defaultEnabledStrategies = hashSetOf("nl.rubensten.texifyidea:Latex", "org.asciidoctor.intellij.asciidoc:AsciiDoc")

    private val instance by lazy { service<GrazieConfig>() }

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
    val prevState = myState
    myState = VersionedState.migrate(state)

    if (prevState != myState || prevState.availableLanguages != myState.availableLanguages) {
      service<GrazieInitializerManager>().publisher.update(prevState, myState)
    }
  }
}
