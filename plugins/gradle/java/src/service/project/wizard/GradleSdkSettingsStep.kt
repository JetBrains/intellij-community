// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.util.projectWizard.JavaSettingsStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.util.*

class GradleSdkSettingsStep(
  private val settingsStep: SettingsStep,
  private val builder: AbstractGradleModuleBuilder
) : JavaSettingsStep(settingsStep, builder, builder::isSuitableSdkType) {

  private val context get() = settingsStep.context
  private val jdk get() = myJdkComboBox.selectedJdk
  private val javaVersion get() = JavaVersion.tryParse(jdk?.versionString)

  override fun validate(): Boolean {
    return super.validate() && validateGradleVersion()
  }

  private fun validateGradleVersion(): Boolean {
    val javaVersion = javaVersion ?: return true
    val gradleVersion = suggestGradleVersion {
      withProject(context.project)
      withJavaVersionFilter(javaVersion)
    }
    if (gradleVersion != null) {
      return true
    }
    return MessageDialogBuilder.yesNo(
      title = GradleBundle.message(
        "gradle.settings.wizard.unsupported.jdk.title",
        if (context.isCreatingNewProject) 0 else 1
      ),
      message = GradleBundle.message(
        "gradle.settings.wizard.unsupported.jdk.message",
        suggestOldestCompatibleJavaVersion(GradleVersion.current()),
        suggestLatestJavaVersion(GradleVersion.current()),
        javaVersion.toFeatureString(),
        GradleVersion.current().version
      )
    )
      .asWarning()
      .ask(component)
  }

  override fun updateDataModel() {
    super.updateDataModel()
    builder.setGradleVersion(suggestGradleVersion {
      withProject(context.project)
      withJavaVersionFilter(javaVersion)
    } ?: GradleVersion.current())
    builder.setGradleDistributionType(DistributionType.DEFAULT_WRAPPED)
    builder.setGradleHome(null)
  }
}