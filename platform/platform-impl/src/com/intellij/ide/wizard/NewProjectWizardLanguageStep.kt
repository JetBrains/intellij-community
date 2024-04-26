// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AbstractObservableProperty
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.ui.dsl.builder.Panel

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@Deprecated("See LanguageNewProjectWizardData documentation for details")
class NewProjectWizardLanguageStep(
  parent: NewProjectWizardStep,
  languageName: String
) : AbstractNewProjectWizardStep(parent),
    LanguageNewProjectWizardData,
    NewProjectWizardBaseData by parent.baseData!! {

  private val languagePropertyBridge = LanguagePropertyBridge(context, propertyGraph, languageName)

  override val languageProperty: GraphProperty<String> = languagePropertyBridge
  override var language: String by languagePropertyBridge

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
  }

  override fun setupUI(builder: Panel) {
    languagePropertyBridge.fireInitEvent()
  }

  private class LanguagePropertyBridge(
    private val context: WizardContext,
    private val propertyGraph: PropertyGraph,
    private val languageName: String
  ) : GraphProperty<String>, AbstractObservableProperty<String>() {

    override fun get(): String {
      return languageName
    }

    /**
     * This method needed to support legacy feature.
     * It selects language in the left tray [com.intellij.ide.projectWizard.ProjectTypeStep] with [GeneratorNewProjectWizard].
     * Use [com.intellij.ide.wizard.comment.LinkNewProjectWizardStep] for navigation between languages.
     */
    override fun set(value: String) {
      val languageModelBuilderId = BaseLanguageGeneratorNewProjectWizard.getLanguageModelBuilderId(context, value)
      context.requestSwitchTo(languageModelBuilderId)
    }

    /**
     * This method needed to support legacy feature.
     * It submits all change events to consumers who initialize background tasks when their language is selected.
     */
    fun fireInitEvent() {
      fireChangeEvent(languageName)
    }

    //@formatter:off
    override fun dependsOn(parent: ObservableProperty<*>, update: () -> String) =
      propertyGraph.dependsOn(this, parent, update = update)
    override fun dependsOn(parent: ObservableProperty<*>, deleteWhenModified: Boolean, update: () -> String) =
      propertyGraph.dependsOn(this, parent, deleteWhenModified, update = update)
    override fun afterPropagation(listener: () -> Unit) =
      propertyGraph.afterPropagation(listener = listener)
    override fun afterPropagation(parentDisposable: Disposable?, listener: () -> Unit) =
      propertyGraph.afterPropagation(parentDisposable, listener = listener)
    //@formatter:on
  }
}
