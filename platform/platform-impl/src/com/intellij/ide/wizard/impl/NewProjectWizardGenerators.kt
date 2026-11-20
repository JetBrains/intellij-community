// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NewProjectWizardGenerators")

package com.intellij.ide.wizard.impl

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.LanguageNewProjectWizard
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LegacyLanguageGeneratorNewProjectWizard
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Collects the enabled language generators of [LanguageGeneratorNewProjectWizard.EP_NAME] and [LanguageNewProjectWizard.EP_NAME].
 * The result is in the order of [GeneratorNewProjectWizard.ordinal].
 */
@ApiStatus.Internal
fun collectLanguageGeneratorNewProjectWizards(context: WizardContext): List<GeneratorNewProjectWizard> {
  val generators = ArrayList<GeneratorNewProjectWizard>()
  @Suppress("DEPRECATION")
  LanguageNewProjectWizard.EP_NAME.forEachExtensionSafe { wizard ->
    generators.add(LegacyLanguageGeneratorNewProjectWizard(context, wizard))
  }
  LanguageGeneratorNewProjectWizard.EP_NAME.forEachExtensionSafe { wizard ->
    generators.add(BaseLanguageGeneratorNewProjectWizard(context, wizard))
  }
  generators.removeIf { !it.isEnabled() }
  generators.sortBy { it.ordinal }
  return generators
}

/**
 * Collects the generators of [GeneratorNewProjectWizard.EP_NAME]. Only a new project wizard shows them, so the result is empty
 * in a new module wizard.
 */
@ApiStatus.Internal
fun collectGeneratorNewProjectWizards(context: WizardContext): List<GeneratorNewProjectWizard> {
  if (!context.isCreatingNewProject) {
    return emptyList()
  }
  val generators = ArrayList<GeneratorNewProjectWizard>()
  GeneratorNewProjectWizard.EP_NAME.forEachExtensionSafe { wizard ->
    generators.add(wizard)
  }
  return generators
}

/**
 * Receives the language generators that a dynamic plugin adds or removes while the wizard is open.
 */
@ApiStatus.Internal
interface LanguageGeneratorListener {
  fun generatorAdded(generator: GeneratorNewProjectWizard)

  /**
   * @param name the [GeneratorNewProjectWizard.name] of the removed generator.
   */
  fun generatorRemoved(name: String)
}

/**
 * Calls [listener] for each language generator that a dynamic plugin adds or removes until the [context] is disposed.
 */
@ApiStatus.Internal
fun installLanguageGeneratorListener(context: WizardContext, listener: LanguageGeneratorListener) {
  @Suppress("DEPRECATION")
  LanguageNewProjectWizard.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<LanguageNewProjectWizard> {
    override fun extensionAdded(extension: LanguageNewProjectWizard, pluginDescriptor: PluginDescriptor) {
      listener.generatorAdded(LegacyLanguageGeneratorNewProjectWizard(context, extension))
    }

    override fun extensionRemoved(extension: LanguageNewProjectWizard, pluginDescriptor: PluginDescriptor) {
      listener.generatorRemoved(extension.name)
    }
  }, context.disposable)
  LanguageGeneratorNewProjectWizard.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<LanguageGeneratorNewProjectWizard> {
    override fun extensionAdded(extension: LanguageGeneratorNewProjectWizard, pluginDescriptor: PluginDescriptor) {
      listener.generatorAdded(BaseLanguageGeneratorNewProjectWizard(context, extension))
    }

    override fun extensionRemoved(extension: LanguageGeneratorNewProjectWizard, pluginDescriptor: PluginDescriptor) {
      listener.generatorRemoved(extension.name)
    }
  }, context.disposable)
}
