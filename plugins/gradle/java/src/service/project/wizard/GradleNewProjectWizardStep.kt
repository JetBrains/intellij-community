// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBox
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBoxConverter
import com.intellij.openapi.externalSystem.service.ui.completion.whenItemChangedFromUi
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.bindEnumStorage
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.validation.CHECK_DIRECTORY
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.WHEN_GRAPH_PROPAGATION_FINISHED
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.util.minimumWidth
import com.intellij.util.lang.JavaVersion
import com.intellij.util.ui.JBUI
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isGradleOlderThan
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.GradleInstallationManager.getGradleVersionSafe
import org.jetbrains.plugins.gradle.service.project.open.suggestGradleHome
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.DistributionTypeItem.LOCAL
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.DistributionTypeItem.WRAPPER
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleDistributionChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleDistributionFinished
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleDslChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleDslFinished
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleVersionChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.Companion.logGradleVersionFinished
import org.jetbrains.plugins.gradle.service.settings.PlaceholderGroup.Companion.placeholderGroup
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.util.*
import javax.swing.Icon

abstract class GradleNewProjectWizardStep<ParentStep>(parent: ParentStep) :
  MavenizedNewProjectWizardStep<ProjectData, ParentStep>(parent), GradleNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  final override val sdkProperty = propertyGraph.property<Sdk?>(null)
  final override val gradleDslProperty = propertyGraph.property(GradleDsl.KOTLIN)
    .bindEnumStorage("NewProjectWizard.gradleDslState")

  final override var sdk by sdkProperty
  final override var gradleDsl by gradleDslProperty

  private val distributionTypeProperty = propertyGraph.lazyProperty { suggestDistributionType() }
  private val gradleVersionProperty = propertyGraph.lazyProperty { suggestGradleVersion() }
  private val gradleVersionsProperty = propertyGraph.lazyProperty { suggestGradleVersions() }
  private val autoSelectGradleVersionProperty = propertyGraph.lazyProperty { suggestAutoSelectGradleVersion() }
  private val gradleHomeProperty = propertyGraph.lazyProperty { suggestGradleHome() }
  private val updateDefaultProjectSettingsProperty = propertyGraph.lazyProperty { true }

  private var distributionType by distributionTypeProperty
  private var gradleVersion by gradleVersionProperty
  private var autoSelectGradleVersion by autoSelectGradleVersionProperty
  private var gradleHome by gradleHomeProperty
  private var updateDefaultProjectSettings by updateDefaultProjectSettingsProperty

  init {
    gradleVersionProperty.dependsOn(sdkProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionProperty.dependsOn(autoSelectGradleVersionProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionProperty.dependsOn(updateDefaultProjectSettingsProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionsProperty.dependsOn(sdkProperty, deleteWhenModified = false) {
      suggestGradleVersions()
    }
  }

  override fun createView(data: ProjectData) = GradleDataView(data)

  protected fun setupJavaSdkUI(builder: Panel) {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
      sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
        .columns(COLUMNS_MEDIUM)
        .whenItemSelectedFromUi { logSdkChanged(sdk) }
        .onApply { logSdkFinished(sdk) }
    }.bottomGap(BottomGap.SMALL)
  }

  protected fun setupGradleDslUI(builder: Panel) {
    builder.row(GradleBundle.message("gradle.dsl.new.project.wizard")) {
      segmentedButton(listOf(GradleDsl.KOTLIN, GradleDsl.GROOVY)) { it.text }
        .bind(gradleDslProperty)
        .whenItemSelectedFromUi { logGradleDslChanged(gradleDsl) }
    }.bottomGap(BottomGap.SMALL)
    builder.onApply { logGradleDslFinished(gradleDsl) }
  }

  protected fun setupGradleDistributionUI(builder: Panel) {
    builder.panel {
      row {
        label(GradleBundle.message("gradle.project.settings.distribution.npw"))
          .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
        comboBox(listOf(WRAPPER, LOCAL), listCellRenderer { text = it.text })
          .columns(COLUMNS_SHORT)
          .bindItem(distributionTypeProperty)
          .whenItemSelectedFromUi { logGradleDistributionChanged(distributionType.value) }
          .onApply { logGradleDistributionFinished(distributionType.value) }
      }
      row {
        placeholderGroup {
          component(WRAPPER) {
            row {
              label(GradleBundle.message("gradle.project.settings.distribution.wrapper.version.npw"))
                .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
              cell(TextCompletionComboBox(context.project, TextCompletionComboBoxConverter.Default()))
                .columns(8)
                .applyToComponent { bindSelectedItem(gradleVersionProperty) }
                .applyToComponent { bindCompletionVariants(gradleVersionsProperty) }
                .trimmedTextValidation(CHECK_NON_EMPTY)
                .validationOnInput { validateGradleVersion(gradleVersion, withDialog = false) }
                .validationOnApply { validateGradleVersion(gradleVersion, withDialog = true) }
                .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
                .enabledIf(autoSelectGradleVersionProperty.not())
                .whenItemChangedFromUi { logGradleVersionChanged(gradleVersion) }
                .onApply { logGradleVersionFinished(gradleVersion) }
              checkBox(GradleBundle.message("gradle.project.settings.distribution.wrapper.version.auto.select"))
                .bindSelected(autoSelectGradleVersionProperty)
            }
          }
          component(LOCAL) {
            row {
              label(GradleBundle.message("gradle.project.settings.distribution.local.location.npw"))
                .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
              val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withPathToTextConvertor(::getPresentablePath)
                .withTextToPathConvertor(::getCanonicalPath)
              val title = GradleBundle.message("gradle.project.settings.distribution.local.location.dialog")
              textFieldWithBrowseButton(title, context.project, fileChooserDescriptor)
                .applyToComponent { setEmptyState(GradleBundle.message("gradle.project.settings.distribution.local.location.empty.state")) }
                .bindText(gradleHomeProperty.toUiPathProperty())
                .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_DIRECTORY)
                .validationOnInput { validateGradleHome(withDialog = false) }
                .validationOnApply { validateGradleHome(withDialog = true) }
                .align(AlignX.FILL)
            }
          }
        }.bindSelectedComponent(distributionTypeProperty)
      }
      row {
        label("")
          .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
        checkBox(GradleBundle.message("gradle.project.settings.distribution.store.settings"))
          .bindSelected(updateDefaultProjectSettingsProperty)
          .onApply {
            if (updateDefaultProjectSettings) {
              GradleDefaultProjectSettings.setInstance(getCurrentDefaultProjectSettings())
            }
          }
      }.bottomGap(BottomGap.SMALL)
    }.visibleIf(parentProperty.transform { !it.isPresent })
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

  override fun findAllParents(): List<ProjectData> {
    val project = context.project ?: return emptyList()
    return ProjectDataManager.getInstance()
      .getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
      .mapNotNull { it.externalProjectStructure }
      .map { it.data }
  }

  override fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    if (artifactId != parentStep.name) {
      return error(ExternalSystemBundle.message(
        "external.system.mavenized.structure.wizard.name.and.artifact.id.is.different.error",
        context.isCreatingNewProjectInt
      ))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateGradleVersion(rawGradleVersion: String, withDialog: Boolean): ValidationInfo? {
    val gradleVersion = try {
      GradleVersion.version(rawGradleVersion)
    }
    catch (ex: IllegalArgumentException) {
      return error(ex.localizedMessage)
    }
    return validateJavaCompatibility(withDialog, gradleVersion)
           ?: validateGradleDslCompatibility(withDialog, gradleVersion)
  }

  private fun ValidationInfoBuilder.validateJavaCompatibility(withDialog: Boolean, gradleVersion: GradleVersion): ValidationInfo? {
    val javaVersion = getJdkVersion()
    if (javaVersion != null && !isSupported(gradleVersion, javaVersion)) {
      return validationWithDialog(
        withDialog = withDialog,
        message = GradleBundle.message(
          "gradle.project.settings.distribution.version.unsupported",
          javaVersion.toFeatureString(),
          gradleVersion.version
        ),
        dialogTitle = GradleBundle.message(
          "gradle.settings.wizard.unsupported.jdk.title",
          context.isCreatingNewProjectInt
        ),
        dialogMessage = GradleBundle.message(
          "gradle.settings.wizard.unsupported.jdk.message",
          (gradleVersion),
          suggestLatestJavaVersion(gradleVersion),
          javaVersion.toFeatureString(),
          gradleVersion.version
        )
      )
    }
    return null
  }

  private fun ValidationInfoBuilder.validateGradleDslCompatibility(withDialog: Boolean, gradleVersion: GradleVersion): ValidationInfo? {
    val oldestCompatibleGradle = "4.0"
    if (gradleDsl == GradleDsl.KOTLIN && gradleVersion.isGradleOlderThan(oldestCompatibleGradle)) {
      return validationWithDialog(
        withDialog = withDialog,
        message = GradleBundle.message(
          "gradle.project.settings.kotlin.dsl.unsupported",
          gradleVersion.version
        ),
        dialogTitle = GradleBundle.message(
          "gradle.project.settings.kotlin.dsl.unsupported.title",
          context.isCreatingNewProjectInt
        ),
        dialogMessage = GradleBundle.message(
          "gradle.project.settings.kotlin.dsl.unsupported.message",
          oldestCompatibleGradle,
          gradleVersion.version
        )
      )
    }
    return null
  }

  private fun ValidationInfoBuilder.validationWithDialog(
    withDialog: Boolean, // dialog shouldn't be shown on text input
    message: @NlsContexts.DialogMessage String,
    dialogTitle: @NlsContexts.DialogTitle String,
    dialogMessage: @NlsContexts.DialogMessage String
  ): ValidationInfo? {
    if (!withDialog) {
      return error(message)
    }
    val dialog = MessageDialogBuilder.yesNo(dialogTitle, dialogMessage).asWarning()
    if (!dialog.ask(component)) {
      return error(message)
    }
    return null
  }

  private fun ValidationInfoBuilder.validateGradleHome(withDialog: Boolean): ValidationInfo? {
    val installationManager = GradleInstallationManager.getInstance()
    if (!installationManager.isGradleSdkHome(context.project, gradleHome)) {
      return error(GradleBundle.message("gradle.project.settings.distribution.invalid"))
    }
    val gradleVersion = GradleInstallationManager.getGradleVersion(gradleHome)
    if (gradleVersion == null) {
      return error(GradleBundle.message("gradle.project.settings.distribution.version.invalid"))
    }
    return validateGradleVersion(gradleVersion, withDialog)
  }

  private fun getJdkVersion(): JavaVersion? {
    val jdk = sdk ?: return null
    val versionString = jdk.versionString ?: return null
    return JavaVersion.tryParse(versionString)
  }

  private fun suggestDistributionType(): DistributionTypeItem {
    return DistributionTypeItem.valueOf(GradleDefaultProjectSettings.getInstance().distributionType)
  }

  private fun suggestGradleVersion(): String {
    val gradleVersion = suggestGradleVersion {
      withProject(context.project)
      withJavaVersionFilter(getJdkVersion())
      if (autoSelectGradleVersion) {
        dontCheckDefaultProjectSettingsVersion()
      }
    } ?: GradleVersion.current()
    return gradleVersion.version
  }

  private fun suggestGradleVersions(): List<String> {
    return when (val javaVersion = getJdkVersion()) {
      null -> getAllSupportedGradleVersions().map { it.version }
      else -> getSupportedGradleVersions(javaVersion).map { it.version }
    }
  }

  private fun suggestAutoSelectGradleVersion(): Boolean {
    return GradleDefaultProjectSettings.getInstance().gradleVersion == null
  }

  private fun suggestGradleHome(): String {
    return suggestGradleHome(context.project) ?: ""
  }

  protected fun linkGradleProject(
    project: Project,
    configureBuildScript: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    val builder = InternalGradleModuleBuilder()
    builder.moduleJdk = sdk
    builder.name = parentStep.name
    builder.contentEntryPath = parentStep.path + "/" + parentStep.name

    builder.isCreatingNewProject = context.isCreatingNewProject

    builder.parentProject = parentData
    builder.projectId = ProjectId(groupId, artifactId, version)
    builder.isInheritGroupId = parentData?.group == groupId
    builder.isInheritVersion = parentData?.version == version

    builder.isUseKotlinDsl = gradleDsl == GradleDsl.KOTLIN

    builder.setGradleVersion(
      GradleVersion.version(
        when (distributionType) {
          WRAPPER -> gradleVersion
          LOCAL -> GradleInstallationManager.getGradleVersion(gradleHome)!!
        }
      )
    )
    builder.setGradleDistributionType(distributionType.value)
    builder.setGradleHome(gradleHome)

    builder.configureBuildScript {
      it.configureBuildScript()
    }

    val model = context.getUserData(NewProjectWizardStep.MODIFIABLE_MODULE_MODEL_KEY)
    builder.commit(project, model)
  }

  class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
    override val location: String = data.linkedExternalProjectPath
    override val icon: Icon = GradleIcons.GradleFile
    override val presentationName: String = data.externalName
    override val groupId: String = data.group ?: ""
    override val version: String = data.version ?: ""
  }

  enum class GradleDsl(val text: @Nls String) {
    KOTLIN(GradleBundle.message("gradle.dsl.new.project.wizard.kotlin")),
    GROOVY(GradleBundle.message("gradle.dsl.new.project.wizard.groovy"))
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

    private val MINIMUM_LABEL_WIDTH = JBUI.scale(120)
  }
}