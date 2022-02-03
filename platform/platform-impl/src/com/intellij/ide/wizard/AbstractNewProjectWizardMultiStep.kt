// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor

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

  override fun initSteps() = epName.extensionList
    .sortedBy { it.ordinal }
    .filter { it.isEnabled(context) }
    .associateTo(LinkedHashMap()) { it.name to it.createStep(self) }

  protected abstract val self: S
}