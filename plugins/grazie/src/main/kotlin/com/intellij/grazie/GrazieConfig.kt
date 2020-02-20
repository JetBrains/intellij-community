// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import com.intellij.grazie.detection.DetectionContext
import com.intellij.grazie.grammar.suppress.SuppressionContext
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.orTrue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
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
    @Property val enabledCommitIntegration: Boolean = false,
    @Property val userDisabledRules: Set<String> = HashSet(),
    @Property val userEnabledRules: Set<String> = HashSet(),
    @Property val suppressionContext: SuppressionContext = SuppressionContext(),
    @Property val detectionContext: DetectionContext.State = DetectionContext.State()
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

    fun hasMissedLanguages(withNative: Boolean = true) = (withNative && nativeLanguage.jLanguage == null) || enabledLanguages.any { it.jLanguage == null }
  }

  companion object {
    private val defaultEnabledProgrammingLanguages by lazy {
      when {
        GraziePlugin.isBundled && ApplicationManager.getApplication()?.isUnitTestMode?.not().orTrue() -> {
          setOf("AsciiDoc", "Latex", "Markdown")
        }
        else -> setOf(
          "AsciiDoc", "Latex", "Markdown",
          "JAVA",
          "JavaScript", "JavaScript 1.5", "JavaScript 1.8",
          "JSX Harmony", "ECMAScript 6",
          "JSON", "JSON5", "HTML", "XML", "yaml",
          "Python",
          "Properties", "TEXT",
          "go", "Rust"
        )
      }.toHashSet()
    }

    private val instance: GrazieConfig by lazy { ServiceManager.getService(GrazieConfig::class.java) }

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
    myState = state

    if (prevState != myState || prevState.availableLanguages != myState.availableLanguages) {
      GrazieStateLifecycle.publisher.update(prevState, myState)
    }
  }
}
