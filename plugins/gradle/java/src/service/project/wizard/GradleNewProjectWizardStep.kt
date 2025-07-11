// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.ide.projectWizard.projectWizardJdkComboBox
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardStep
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBox
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBoxConverter
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.bindEnumStorage
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.validation.CHECK_DIRECTORY
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.WHEN_GRAPH_PROPAGATION_FINISHED
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.util.minimumWidth
import com.intellij.util.lang.JavaVersion
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GradleIcons
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.isFoojayPluginSupported
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.service.project.open.suggestGradleHome
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.DistributionTypeItem.LOCAL
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep.DistributionTypeItem.WRAPPER
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleDistributionChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleDistributionFinished
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleDslChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleDslFinished
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleVersionChanged
import org.jetbrains.plugins.gradle.service.project.wizard.statistics.GradleNewProjectWizardCollector.logGradleVersionFinished
import org.jetbrains.plugins.gradle.service.settings.PlaceholderGroup.Companion.placeholderGroup
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleDefaultProjectSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.suggestGradleVersion
import java.nio.file.Path
import javax.swing.Icon

abstract class GradleNewProjectWizardStep<ParentStep>(parent: ParentStep) :
  MavenizedNewProjectWizardStep<ProjectData, ParentStep>(parent), GradleNewProjectWizardData
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  final override val jdkIntentProperty: GraphProperty<ProjectWizardJdkIntent?> = propertyGraph.property(null)
  final override val gradleDslProperty: GraphProperty<GradleDsl> = propertyGraph.property(GradleDsl.KOTLIN)
    .bindEnumStorage("NewProjectWizard.gradleDslState")

  final override var jdkIntent: ProjectWizardJdkIntent? by jdkIntentProperty
  final override var gradleDsl: GradleDsl by gradleDslProperty

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
    gradleVersionProperty.dependsOn(jdkIntentProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionProperty.dependsOn(autoSelectGradleVersionProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionProperty.dependsOn(updateDefaultProjectSettingsProperty, deleteWhenModified = false) {
      if (autoSelectGradleVersion) suggestGradleVersion() else gradleVersion
    }
    gradleVersionsProperty.dependsOn(jdkIntentProperty, deleteWhenModified = false) {
      suggestGradleVersions()
    }
  }

  override fun createView(data: ProjectData): GradleDataView {
    return GradleDataView(data)
  }

  protected fun setupJavaSdkUI(builder: Panel) {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
      projectWizardJdkComboBox(this, jdkIntentProperty)
        .validationOnInput { validateJavaSdk(withDialog = false) }
        .validationOnApply { validateJavaSdk(withDialog = true) }
        .whenItemSelectedFromUi { jdkIntent?.javaVersion?.let { logSdkChanged(it.feature) } }
        .onApply { jdkIntent?.javaVersion?.let { logSdkFinished(it.feature) } }
    }.bottomGap(BottomGap.SMALL)
  }

  protected fun setupGradleDslUI(builder: Panel) {
    builder.row(GradleBundle.message("gradle.dsl.new.project.wizard")) {
      val renderer: SegmentedButton.ItemPresentation.(GradleDsl) -> Unit = {
        text = when (it) {
          GradleDsl.GROOVY -> GradleBundle.message("gradle.dsl.new.project.wizard.groovy")
          GradleDsl.KOTLIN -> GradleBundle.message("gradle.dsl.new.project.wizard.kotlin")
        }
      }
      segmentedButton(listOf(GradleDsl.KOTLIN, GradleDsl.GROOVY), renderer)
        .bind(gradleDslProperty)
        .whenItemSelectedFromUi { logGradleDslChanged(gradleDsl) }
    }.bottomGap(BottomGap.SMALL)
    builder.onApply { logGradleDslFinished(gradleDsl) }
  }

  protected open val distributionTypes: List<DistributionTypeItem> = listOf(WRAPPER, LOCAL)

  protected fun setupGradleDistributionUI(builder: Panel) {
    builder.panel {
      row {
        label(GradleBundle.message("gradle.project.settings.distribution.npw"))
          .applyToComponent { minimumWidth = MINIMUM_LABEL_WIDTH }
        comboBox(distributionTypes, textListCellRenderer { it?.text })
          .columns(COLUMNS_SHORT)
          .bindItem(distributionTypeProperty)
          .whenItemSelectedFromUi { logGradleDistributionChanged(distributionType.value) }
          .onApply { logGradleDistributionFinished(distributionType.value) }
      }.visibleIf(parentProperty.transform { distributionTypes.size > 1 })
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
                .withTitle(GradleBundle.message("gradle.project.settings.distribution.local.location.dialog"))
                .withPathToTextConvertor(::getPresentablePath)
                .withTextToPathConvertor(::getCanonicalPath)
              textFieldWithBrowseButton(fileChooserDescriptor, context.project)
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
              GradleDefaultProjectSettings.setInstance(getDefaultProjectSettings())
            }
          }
      }.bottomGap(BottomGap.SMALL)
    }.visibleIf(parentProperty.transform { !it.isPresent })
  }

  private fun <T, C : TextCompletionComboBox<T>> Cell<C>.whenItemChangedFromUi(
    parentDisposable: Disposable? = null,
    listener: (T) -> Unit
  ): Cell<C> {
    return applyToComponent {
      whenItemChangedFromUi(parentDisposable, listener)
    }
  }

  private fun getDefaultProjectSettings(): GradleDefaultProjectSettings {
    val settings = GradleDefaultProjectSettings.getInstance().copy()
    settings.distributionType = distributionType.value
    when (distributionType) {
      WRAPPER -> when (autoSelectGradleVersion) {
        true -> settings.gradleVersion = null
        else -> settings.gradleVersion = GradleVersion.version(gradleVersion)
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

  private fun ValidationInfoBuilder.validateJavaSdk(withDialog: Boolean): ValidationInfo? {
    val javaVersion = getJdkVersion()
    if (javaVersion == null) {
      return null
    }
    return validateIdeaJavaCompatibility(withDialog, javaVersion)
  }

  private fun ValidationInfoBuilder.validateIdeaJavaCompatibility(withDialog: Boolean, javaVersion: JavaVersion): ValidationInfo? {
    if (GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)) {
      return null
    }
    val oldestSupportedJavaVersion = GradleJvmSupportMatrix.getOldestSupportedJavaVersionByIdea()
    return errorWithDialog(
      withDialog = withDialog,
      message = GradleBundle.message(
        "gradle.settings.wizard.java.unsupported.message",
        ApplicationNamesInfo.getInstance().fullProductName,
        oldestSupportedJavaVersion.toFeatureString()
      ),
      dialogTitle = GradleBundle.message(
        "gradle.settings.wizard.java.unsupported.title"
      ),
      dialogMessage = GradleBundle.message(
        "gradle.settings.wizard.java.unsupported.message",
        ApplicationNamesInfo.getInstance().fullProductName,
        oldestSupportedJavaVersion.toFeatureString(),
      )
    )
  }

  private fun ValidationInfoBuilder.validateIdeaGradleCompatibility(withDialog: Boolean, gradleVersion: GradleVersion): ValidationInfo? {
    if (GradleJvmSupportMatrix.isGradleSupportedByIdea(gradleVersion)) {
      return null
    }
    val oldestSupportedGradleVersion = GradleJvmSupportMatrix.getOldestSupportedGradleVersionByIdea()
    return errorWithDialog(
      withDialog = withDialog,
      message = GradleBundle.message(
        "gradle.settings.wizard.gradle.unsupported.message",
        ApplicationNamesInfo.getInstance().fullProductName,
        oldestSupportedGradleVersion.version
      ),
      dialogTitle = GradleBundle.message(
        "gradle.settings.wizard.gradle.unsupported.title"
      ),
      dialogMessage = GradleBundle.message(
        "gradle.settings.wizard.gradle.unsupported.message",
        ApplicationNamesInfo.getInstance().fullProductName,
        oldestSupportedGradleVersion.version,
      )
    )
  }

  /**
   * Is the language of this wizard compatible with the [gradleVersion].
   */
  protected open fun validateGradleVersion(gradleVersion: GradleVersion): Boolean {
    return true
  }

  /**
   * Validate that the language is compatible with Gradle and return
   * an appropriate error message if not.
   */
  protected open fun validateGradleVersion(
    builder: ValidationInfoBuilder,
    gradleVersion: GradleVersion,
    withDialog: Boolean
  ): ValidationInfo? = null

  private fun validateJdkCompatibility(gradleVersion: GradleVersion): Boolean {
    val javaVersion = getJdkVersion()
    return javaVersion == null || GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)
  }

  /**
   * Validate that the language is compatible with Gradle and return
   * an appropriate error message if not.
   */
  private fun ValidationInfoBuilder.validateJdkCompatibility(gradleVersion: GradleVersion, withDialog: Boolean): ValidationInfo? {
    val javaVersion = getJdkVersion()
    if (javaVersion == null || GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)) return null
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
        GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion),
        GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(gradleVersion),
        javaVersion.toFeatureString(),
        gradleVersion.version
      )
    )
  }

  private fun ValidationInfoBuilder.validateGradleVersion(rawGradleVersion: String, withDialog: Boolean): ValidationInfo? {
    val gradleVersion = try {
      GradleVersion.version(rawGradleVersion)
    }
    catch (ex: IllegalArgumentException) {
      return error(ex.localizedMessage)
    }
    return validateIdeaGradleCompatibility(withDialog, gradleVersion)
           ?: validateJdkCompatibility(gradleVersion, withDialog)
           ?: validateGradleVersion(this, gradleVersion, withDialog)
  }

  protected fun ValidationInfoBuilder.validationWithDialog(
    withDialog: Boolean, // dialog shouldn't be shown on text input
    message: @NlsContexts.DialogMessage String,
    dialogTitle: @NlsContexts.DialogTitle String,
    dialogMessage: @NlsContexts.DialogMessage String
  ): ValidationInfo? {
    if (withDialog) {
      val isContinue = MessageDialogBuilder.yesNo(dialogTitle, dialogMessage)
        .icon(UIUtil.getWarningIcon())
        .ask(component)
      if (isContinue) {
        return null
      }
    }
    return error(message)
  }

  protected fun ValidationInfoBuilder.errorWithDialog(
    withDialog: Boolean, // dialog shouldn't be shown on text input
    message: @NlsContexts.DialogMessage String,
    dialogTitle: @NlsContexts.DialogTitle String,
    dialogMessage: @NlsContexts.DialogMessage String
  ): ValidationInfo {
    if (withDialog) {
      MessageDialogBuilder.Message(dialogTitle, dialogMessage)
        .buttons(CommonBundle.getOkButtonText())
        .defaultButton(CommonBundle.getOkButtonText())
        .focusedButton(CommonBundle.getOkButtonText())
        .icon(UIUtil.getErrorIcon())
        .show(parentComponent = component)
    }
    return error(message)
  }

  private fun ValidationInfoBuilder.validateGradleHome(withDialog: Boolean): ValidationInfo? {
    val gradleHomePath = Path.of(gradleHome)
    if (!GradleInstallationManager.getInstance().isGradleSdkHome(context.project, gradleHomePath)) {
      return error(GradleBundle.message("gradle.project.settings.distribution.invalid"))
    }
    val gradleVersion = GradleInstallationManager.getGradleVersion(gradleHomePath)
    if (gradleVersion == null) {
      return error(GradleBundle.message("gradle.project.settings.distribution.version.invalid"))
    }
    return validateGradleVersion(gradleVersion, withDialog)
  }

  private fun getJdkVersion(): JavaVersion? {
    return JavaVersion.tryParse(jdkIntent?.versionString)
  }

  private fun suggestDistributionType(): DistributionTypeItem {
    val defaultDistributionType = DistributionTypeItem.valueOf(GradleDefaultProjectSettings.getInstance().distributionType)
    if (distributionTypes.contains(defaultDistributionType)) {
      return defaultDistributionType
    }
    return distributionTypes.firstOrNull() ?: defaultDistributionType
  }

  private fun suggestGradleVersion(): String {
    val gradleVersion = suggestGradleVersion {
      withProject(context.project)
      withJavaVersionFilter(getJdkVersion())
      withFilter {
        validateJdkCompatibility(it) && validateGradleVersion(it)
      }
      if (autoSelectGradleVersion) {
        dontCheckDefaultProjectSettingsVersion()
      }
    } ?: GradleVersion.current()
    return gradleVersion.version
  }

  protected open fun suggestGradleVersions(): List<String> {
    return GradleJvmSupportMatrix.getAllSupportedGradleVersionsByIdea().filter {
      validateJdkCompatibility(it) && validateGradleVersion(it)
    }.map { it.version }
  }

  private fun suggestAutoSelectGradleVersion(): Boolean {
    return GradleDefaultProjectSettings.getInstance().gradleVersion == null
  }

  private fun suggestGradleHome(): String {
    return suggestGradleHome(context.project) ?: ""
  }

  private fun startJdkDownloadIfNeeded(module: Module) {
    val sdkDownloadTask = jdkIntent?.downloadTask
    if (sdkDownloadTask is JdkDownloadTask) {
      // Download the SDK on project creation
      module.project.service<JdkDownloadService>().scheduleDownloadJdk(sdkDownloadTask, module, context.isCreatingNewProject)
    }
  }

  val gradleVersionToUse: GradleVersion by lazy {
    val rawGradleVersion = when (distributionType) {
      WRAPPER -> gradleVersion
      LOCAL -> GradleInstallationManager.getGradleVersion(Path.of(gradleHome))!!
    }
    GradleVersion.version(rawGradleVersion)
  }

  val isCreatingNewLinkedProject: Boolean by lazy {
    parentData == null
  }

  val isFoojayPluginSupported: Boolean by lazy {
    resolveIsFoojayPluginSupported()
  }

  protected open fun resolveIsFoojayPluginSupported(): Boolean {
    return isFoojayPluginSupported(gradleVersionToUse)
  }

  val isCreatingDaemonToolchain: Boolean by lazy {
    Registry.`is`("gradle.daemon.jvm.criteria.new.project") &&
    isCreatingNewLinkedProject && isFoojayPluginSupported &&
    GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(gradleVersionToUse)
  }

  @ApiStatus.Internal
  fun setupProjectFromBuilder(project: Project) {
    val builder = object : AbstractGradleModuleBuilder() {}

    builder.moduleJdk = jdkIntent?.prepareJdk()
    builder.sdkDownloadTask = jdkIntent?.downloadTask

    builder.name = parentStep.name
    builder.contentEntryPath = parentStep.path + "/" + parentStep.name

    builder.isCreatingWrapper = false
    builder.isCreatingBuildScriptFile = false
    builder.isCreatingSettingsScriptFile = false
    builder.isCreatingEmptyContentRoots = false
    builder.isCreatingDaemonToolchain = isCreatingDaemonToolchain
    builder.isCreatingNewProject = context.isCreatingNewProject

    builder.parentProject = parentData
    builder.projectId = ProjectId(groupId, artifactId, version)
    builder.isInheritGroupId = parentData?.group == groupId
    builder.isInheritVersion = parentData?.version == version

    builder.isUseKotlinDsl = gradleDsl == GradleDsl.KOTLIN

    builder.setGradleVersion(gradleVersionToUse)
    builder.setGradleDistributionType(distributionType.value)
    builder.setGradleHome(gradleHome)

    setupProjectFromBuilder(project, builder)
      ?.also { startJdkDownloadIfNeeded(it) }
  }

  class GradleDataView(override val data: ProjectData) : DataView<ProjectData>() {
    override val location: String = data.linkedExternalProjectPath
    override val icon: Icon = GradleIcons.GradleFile
    override val presentationName: String = data.externalName
    override val groupId: String = data.group ?: ""
    override val version: String = data.version ?: ""
  }

  protected enum class DistributionTypeItem(val value: DistributionType, val text: @Nls String) {
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
