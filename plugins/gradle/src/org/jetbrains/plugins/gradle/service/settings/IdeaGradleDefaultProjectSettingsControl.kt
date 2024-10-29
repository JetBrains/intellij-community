// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBox
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBoxConverter
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.ui.validation.CHECK_DIRECTORY
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.WHEN_GRAPH_PROPAGATION_FINISHED
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.util.minimumWidth
import com.intellij.util.ui.JBUI
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.GradleInstallationManager.getGradleVersionSafe
import org.jetbrains.plugins.gradle.service.project.open.suggestGradleHome
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleDefaultProjectSettingsControl.DistributionTypeItem.LOCAL
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleDefaultProjectSettingsControl.DistributionTypeItem.WRAPPER
import org.jetbrains.plugins.gradle.service.settings.PlaceholderGroup.Companion.placeholderGroup
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleBundle.message
import org.jetbrains.plugins.gradle.util.suggestGradleVersion

internal class IdeaGradleDefaultProjectSettingsControl : GradleSettingsControl() {
  private val propertyGraph = PropertyGraph()

  private val distributionTypeProperty = propertyGraph.lateinitProperty<DistributionTypeItem>()
  private val gradleVersionProperty = propertyGraph.lateinitProperty<String>()
  private val gradleVersionsProperty = propertyGraph.lateinitProperty<List<String>>()
  private val autoSelectGradleVersionProperty = propertyGraph.lateinitProperty<Boolean>()
  private val gradleHomeProperty = propertyGraph.lateinitProperty<String>()

  private var distributionType by distributionTypeProperty
  private var gradleVersion by gradleVersionProperty
  private var gradleVersions by gradleVersionsProperty
  private var autoSelectGradleVersion by autoSelectGradleVersionProperty
  private var gradleHome by gradleHomeProperty

  init {
    setCurrentDefaultProjectSettings(GradleDefaultProjectSettings.getInstance())
    gradleVersionProperty.dependsOn(autoSelectGradleVersionProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
  }

  override fun setupUi(builder: Panel) {
    builder.panel {
      group(GradleBundle.message("gradle.project.settings.distribution.group")) {
        row {
          label(GradleBundle.message("gradle.project.settings.distribution"))
            .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
          comboBox(listOf(WRAPPER, LOCAL), textListCellRenderer { it?.text })
            .columns(COLUMNS_SHORT)
            .bindItem(distributionTypeProperty)
        }
        row {
          placeholderGroup {
            component(WRAPPER) {
              row {
                label(GradleBundle.message("gradle.project.settings.distribution.wrapper.version"))
                  .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
                cell(TextCompletionComboBox(null, TextCompletionComboBoxConverter.Default()))
                  .columns(8)
                  .applyToComponent { bindSelectedItem(gradleVersionProperty) }
                  .applyToComponent { bindCompletionVariants(gradleVersionsProperty) }
                  .trimmedTextValidation(CHECK_NON_EMPTY)
                  .validationInfo { validateGradleVersion(gradleVersion) }
                  .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
                  .enabledIf(autoSelectGradleVersionProperty.not())
                checkBox(GradleBundle.message("gradle.project.settings.distribution.wrapper.version.auto.select"))
                  .bindSelected(autoSelectGradleVersionProperty)
              }
            }
            component(LOCAL) {
              row {
                label(GradleBundle.message("gradle.project.settings.distribution.local.location"))
                  .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
                val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                  .withTitle(message("gradle.project.settings.distribution.local.location.dialog"))
                  .withPathToTextConvertor(::getPresentablePath)
                  .withTextToPathConvertor(::getCanonicalPath)
                textFieldWithBrowseButton(fileChooserDescriptor)
                  .applyToComponent { setEmptyState(GradleBundle.message("gradle.project.settings.distribution.local.location.empty.state")) }
                  .bindText(gradleHomeProperty.toUiPathProperty())
                  .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_DIRECTORY)
                  .validationInfo { validateGradleHome(gradleHome) }
                  .align(AlignX.FILL)
              }
            }
          }.bindSelectedComponent(distributionTypeProperty)
        }
      }
    }
    builder.onReset {
      setCurrentDefaultProjectSettings(GradleDefaultProjectSettings.getInstance())
    }
    builder.onIsModified {
      GradleDefaultProjectSettings.getInstance() != getCurrentDefaultProjectSettings()
    }
    builder.onApply {
      GradleDefaultProjectSettings.setInstance(getCurrentDefaultProjectSettings())
    }
  }

  private fun setCurrentDefaultProjectSettings(settings: GradleDefaultProjectSettings) {
    distributionType = DistributionTypeItem.valueOf(settings.distributionType)
    gradleVersions = GradleJvmSupportMatrix.getAllSupportedGradleVersionsByIdea().map { it.version }
    autoSelectGradleVersion = settings.gradleVersion == null
    when (distributionType) {
      WRAPPER -> when (autoSelectGradleVersion) {
        true -> gradleVersion = suggestGradleVersion()
        else -> gradleVersion = settings.gradleVersion?.version ?: ""
      }
      LOCAL -> gradleVersion = suggestGradleVersion()
    }
    when (distributionType) {
      WRAPPER -> gradleHome = suggestGradleHome(null) ?: ""
      LOCAL -> gradleHome = settings.gradleHome ?: ""
    }
  }

  private fun getCurrentDefaultProjectSettings(): GradleDefaultProjectSettings {
    val settings = GradleDefaultProjectSettings.getInstance().copy()
    settings.distributionType = distributionType.value
    when (distributionType) {
      WRAPPER -> when (autoSelectGradleVersion) {
        true -> settings.gradleVersion = null
        else -> settings.gradleVersion = getGradleVersionSafe(gradleVersion)
      }
      LOCAL -> settings.gradleVersion = null
    }
    when (distributionType) {
      WRAPPER -> settings.gradleHome = null
      LOCAL -> settings.gradleHome = gradleHome
    }
    return settings
  }

  private fun ValidationInfoBuilder.validateGradleVersion(rawGradleVersion: String): ValidationInfo? {
    try {
      GradleVersion.version(rawGradleVersion)
    }
    catch (ex: IllegalArgumentException) {
      return error(ex.localizedMessage)
    }
    return null
  }

  private fun ValidationInfoBuilder.validateGradleHome(gradleHome: String): ValidationInfo? {
    val installationManager = GradleInstallationManager.getInstance()
    if (!installationManager.isGradleSdkHome(null, gradleHome)) {
      return error(GradleBundle.message("gradle.project.settings.distribution.invalid"))
    }
    val rawGradleVersion = GradleInstallationManager.getGradleVersion(gradleHome)
    if (rawGradleVersion == null) {
      return error(GradleBundle.message("gradle.project.settings.distribution.version.invalid"))
    }
    return validateGradleVersion(rawGradleVersion)
  }

  private fun suggestGradleVersion(): String {
    val gradleVersion = suggestGradleVersion {
      dontCheckDefaultProjectSettingsVersion()
    } ?: GradleVersion.current()
    return gradleVersion.version
  }

  private enum class DistributionTypeItem(val value: DistributionType, val text: @Nls String) {
    WRAPPER(DistributionType.DEFAULT_WRAPPED, GradleBundle.message("gradle.project.settings.distribution.wrapper")),
    LOCAL(DistributionType.LOCAL, GradleBundle.message("gradle.project.settings.distribution.local"));

    companion object {

      fun valueOf(type: DistributionType): DistributionTypeItem {
        return when (type) {
          DistributionType.BUNDLED -> WRAPPER
          DistributionType.DEFAULT_WRAPPED -> WRAPPER
          DistributionType.WRAPPED -> WRAPPER
          DistributionType.LOCAL -> LOCAL
        }
      }
    }
  }

  companion object {

    private val MINIMUM_LABEL_WIDTH = JBUI.scale(90)
  }
}
