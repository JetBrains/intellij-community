// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.grazie.config.CheckingContext
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.config.migration.VersionedState
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.msg.GrazieInitializerManager
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.Rule
import com.intellij.openapi.components.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.VisibleForTesting
import java.util.*

@State(name = "GraziConfig", presentableName = GrazieConfig.PresentableNameGetter::class, storages = [
  Storage("grazie_global.xml"),
  Storage(value = "grazi_global.xml", deprecated = true)
], category = SettingsCategory.CODE)
class GrazieConfig : PersistentStateComponent<GrazieConfig.State> {
  @Suppress("unused")
  enum class Version : VersionedState.Version<State> {
    INITIAL,

    //Since commit abc7e5f5
    OLD_UI {
      @Suppress("DEPRECATION")
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
    @Deprecated("Use checkingContext.disabledLanguages") @Property val enabledGrammarStrategies: Set<String> = HashSet(defaultEnabledStrategies),
    @Deprecated("Use checkingContext.disabledLanguages") @Property val disabledGrammarStrategies: Set<String> = HashSet(),
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
     * The available language set depends on currently loaded LanguageTool modules.
     *
     * Note that after loading of a new module, this field will not change. It will
     * remain equal to the moment the field was accessed first time.
     *
     * Lazy is used, because deserialized properties are updated during initial deserialization
     *
     * NOTE: By default, availableLanguages are not included into [equals]. Check for it manually.
     */
    val availableLanguages: Set<Lang> by lazy {
      enabledLanguages.asSequence().filter { lang -> lang.jLanguage != null }.toCollection(CollectionFactory.createSmallMemoryFootprintLinkedSet())
    }

    val missedLanguages: Set<Lang>
      get() = enabledLanguages.asSequence().filter { it.jLanguage == null }.toCollection(CollectionFactory.createSmallMemoryFootprintLinkedSet())

    override fun increment() = copy(version = version.next() ?: error("Attempt to increment latest version $version"))

    fun hasMissedLanguages(): Boolean {
      return enabledLanguages.any { it.jLanguage == null }
    }
  }

  companion object {
    private val defaultEnabledStrategies =
      Collections.unmodifiableSet(hashSetOf("nl.rubensten.texifyidea:Latex", "org.asciidoctor.intellij.asciidoc:AsciiDoc"))

    @VisibleForTesting
    fun migrateLTRuleIds(state: State): State {
      val ltRules: List<Rule> by lazy {
        state.enabledLanguages.filter { it.jLanguage != null }.flatMap { LanguageToolChecker.grammarRules(LangTool.createTool(it, state), it) }
      }

      fun convert(ids: Set<String>): Set<String> =
        ids.flatMap { id ->
          if (id.contains(".")) listOf(id)
          else ltRules.asSequence().map { it.globalId }.filter { it.startsWith("LanguageTool.") && it.endsWith(".$id") }.toList()
        }.toSet()

      return state.copy(userEnabledRules = convert(state.userEnabledRules), userDisabledRules = convert(state.userDisabledRules))
    }

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

    fun stateChanged(prevState: State, newState: State) {
      service<GrazieInitializerManager>().publisher.update(prevState, newState)

      ProjectManager.getInstance().openProjects.forEach {
        DaemonCodeAnalyzer.getInstance(it).restart()
      }
    }
  }

  class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
    override fun get() = GrazieBundle.message("grazie.config.name")
  }

  private var myState = State()

  override fun getState() = myState

  override fun loadState(state: State) {
    val prevState = myState
    myState = migrateLTRuleIds(VersionedState.migrate(state))

    if (prevState != myState) {
      stateChanged(prevState, myState)
    }
  }
}
