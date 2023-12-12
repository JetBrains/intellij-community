// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.ide.util.projectWizard.ModuleBuilder] and register as an extension point.
 */
abstract class GeneratorNewProjectWizardBuilderAdapter(val wizard: GeneratorNewProjectWizard) : AbstractNewProjectWizardBuilder() {
  override fun getBuilderId(): String = NPW_PREFIX + wizard.id
  override fun getPresentableName(): String = wizard.name
  override fun getDescription(): String = wizard.description ?: ""
  override fun getGroupName(): String = wizard.groupName ?: super.getGroupName()
  override fun getNodeIcon(): Icon = wizard.icon
  override fun createStep(context: WizardContext): NewProjectWizardStep = wizard.createStep(context)

  companion object {
    /**
     * NPW generators, which ids start with [NPW_PREFIX], will skip common ProjectSettingsStep.
     *
     * See https://youtrack.jetbrains.com/issue/IDEA-280712 for details
     */
    @ApiStatus.Internal
    const val NPW_PREFIX: String = "NPW."
  }
}