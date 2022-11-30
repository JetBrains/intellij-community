// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.util.projectWizard.JavaSettingsStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.findGradleVersion
import org.jetbrains.plugins.gradle.util.isSupported
import org.jetbrains.plugins.gradle.util.suggestGradleVersion

class GradleSdkSettingsStep(
  private val settingsStep: SettingsStep,
  private val builder: AbstractGradleModuleBuilder
) : JavaSettingsStep(settingsStep, builder, builder::isSuitableSdkType) {

  private val context get() = settingsStep.context
  private val jdk get() = myJdkComboBox.selectedJdk
  private val javaVersion get() = JavaVersion.tryParse(jdk?.versionString)

  private fun getPreferredGradleVersion(): GradleVersion {
    val project = context.project ?: return GradleVersion.current()
    return findGradleVersion(project) ?: GradleVersion.current()
  }

  private fun getGradleVersion(): GradleVersion? {
    val preferredGradleVersion = getPreferredGradleVersion()
    val javaVersion = javaVersion ?: return preferredGradleVersion
    return when (isSupported(preferredGradleVersion, javaVersion)) {
      true -> preferredGradleVersion
      else -> suggestGradleVersion(javaVersion)
    }
  }

  override fun validate(): Boolean {
    return super.validate() && validateGradleVersion()
  }

  private fun validateGradleVersion(): Boolean {
    val javaVersion = javaVersion ?: return true
    if (getGradleVersion() != null) {
      return true
    }
    val preferredGradleVersion = getPreferredGradleVersion()
    val matrix = GradleJvmSupportMatrix.INSTANCE
    return MessageDialogBuilder.yesNo(
      title = GradleBundle.message(
        "gradle.settings.wizard.unsupported.jdk.title",
        if (context.isCreatingNewProject) 0 else 1
      ),
      message = GradleBundle.message(
        "gradle.settings.wizard.unsupported.jdk.message",
        javaVersion.toFeatureString(),
        matrix.minSupportedJava().toFeatureString(),
        matrix.maxSupportedJava().toFeatureString(),
        preferredGradleVersion.version))
      .asWarning()
      .ask(component)
  }

  override fun updateDataModel() {
    super.updateDataModel()
    builder.gradleVersion = getGradleVersion() ?: getPreferredGradleVersion()
  }
}