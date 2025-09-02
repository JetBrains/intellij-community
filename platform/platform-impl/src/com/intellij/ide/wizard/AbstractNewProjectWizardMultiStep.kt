// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.NlsContexts

/**
 * Allows for forking wizard steps and display different steps depending on the user's selection.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#steps-forking-the-wizard-flow">
 *   New Project Wizard API: Steps Forking the Wizard Flow (IntelliJ Platform Docs)</a>
 */
abstract class AbstractNewProjectWizardMultiStep<S : NewProjectWizardStep, F : NewProjectWizardMultiStepFactory<S>>(
  parent: NewProjectWizardStep,
  val epName: ExtensionPointName<F>
) : AbstractNewProjectWizardMultiStepBase(parent) {

  init {
    epName.addExtensionPointListener(
      object : ExtensionPointListener<F> {
        override fun extensionAdded(extension: F, pluginDescriptor: PluginDescriptor) {
          steps = initSteps()
        }

        override fun extensionRemoved(extension: F, pluginDescriptor: PluginDescriptor) {
          steps = initSteps()
        }
      }, context.disposable
    )
  }

  override fun initSteps(): LinkedHashMap<@NlsContexts.Label String, NewProjectWizardStep> = epName.extensionList
    .sortedBy { it.ordinal }
    .filter { it.isEnabled(context) }
    .associateTo(LinkedHashMap()) { it.name to it.createStep(self) }

  protected abstract val self: S
}