// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.CommonBundle
import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logContentRootChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleFileLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleNameChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.distribution.*
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import java.awt.KeyboardFocusManager
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities

class IntelliJGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {

  override val name: String = "IntelliJ"

  override val ordinal: Int = 0

  override fun createStep(parent: GroovyNewProjectWizard.Step): NewProjectWizardStep = Step(parent)

  class Step(parent: GroovyNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    val distributionsProperty = propertyGraph.graphProperty<DistributionInfo?> { null }

    var distribution by distributionsProperty

    override fun Panel.customOptions() {
      row(GroovyBundle.message("label.groovy.sdk")) {
        val groovyLibraryDescription = GroovyLibraryDescription()
        val comboBox = DistributionComboBox(context.project, object : FileChooserInfo {
          override val fileChooserTitle = GroovyBundle.message("dialog.title.select.groovy.sdk")
          override val fileChooserDescription: String? = null
          override val fileChooserDescriptor = groovyLibraryDescription.createFileChooserDescriptor()
          override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
        })
        comboBox.specifyLocationActionName = GroovyBundle.message("dialog.title.specify.groovy.sdk")
        comboBox.addLoadingItem()
        val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
        if (pathToGroovyHome != null) {
          comboBox.addDistributionIfNotExists(LocalDistributionInfo(pathToGroovyHome.path))
        }
        loadLatestGroovyVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
          override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
            versions.sortedWith(::moveUnstableVersionToTheEnd)
              .map(Step::FrameworkLibraryDistributionInfo)
              .forEach(comboBox::addDistributionIfNotExists)
            comboBox.removeLoadingItem()
          }

          override fun onError(errorMessage: String) {
            comboBox.removeLoadingItem()
          }
        })
        cell(comboBox)
          .validationOnApply { validateGroovySdk() }
          .bindItem(distributionsProperty.transform(
            { it?.let(DistributionComboBox.Item::Distribution) ?: DistributionComboBox.Item.NoDistribution },
            { (it as? DistributionComboBox.Item.Distribution)?.info }
          ))
          .validationOnInput { validateGroovySdkPath(it) }
          .columns(COLUMNS_MEDIUM)
      }.bottomGap(BottomGap.SMALL)
    }

    private fun ValidationInfoBuilder.validateGroovySdkPath(comboBox: DistributionComboBox): ValidationInfo? {
      val localDistribution = comboBox.selectedDistribution as? LocalDistributionInfo ?: return null
      val path = localDistribution.path
      if (path.isEmpty()) {
        return error(GroovyBundle.message("dialog.title.validation.path.should.not.be.empty"))
      }
      else if (isInvalidSdk(localDistribution)) {
        return error(GroovyBundle.message("dialog.title.validation.path.does.not.contain.groovy.sdk"))
      }
      else {
        return null
      }
    }

    private fun ValidationInfoBuilder.validateGroovySdk(): ValidationInfo? {
      if (isBlankDistribution(distribution)) {
        if (Messages.showDialog(GroovyBundle.message("dialog.title.no.jdk.specified.prompt"),
                                GroovyBundle.message("dialog.title.no.jdk.specified.title"),
                                arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
                                Messages.getWarningIcon()) != Messages.YES) {
          return error(GroovyBundle.message("dialog.title.no.jdk.specified.error"))
        }
      }
      if (isInvalidSdk(distribution)) {
        if (Messages.showDialog(
            GroovyBundle.message(
              "dialog.title.validation.directory.you.specified.does.not.contain.groovy.sdk.do.you.want.to.create.project.with.this.configuration"),
            GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.title"),
            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
            Messages.getWarningIcon()) != Messages.YES) {
          return error(GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.error"))
        }
      }
      return null
    }

    private fun isBlankDistribution(distribution: DistributionInfo?): Boolean {
      return distribution == null || (distribution is LocalDistributionInfo &&
                                      distribution.path == "")
    }

    private fun isInvalidSdk(distribution: DistributionInfo?): Boolean {
      return distribution == null || (distribution is LocalDistributionInfo &&
                                      GroovyConfigUtils.getInstance().getSDKVersionOrNull(distribution.path) == null)
    }

    override fun setupProject(project: Project) {
      reportFeature()
      val groovyModuleBuilder = GroovyAwareModuleBuilder().apply {
        val contentRoot = FileUtil.toSystemDependentName(contentRoot)
        contentEntryPath = contentRoot
        name = moduleName
        moduleJdk = sdk
        if (addSampleCode) {
          addGroovySample("src")
        }
        val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)
        moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      }

      val librariesContainer = LibrariesContainerFactory.createContainer(project)
      val compositionSettings = createCompositionSettings(project, librariesContainer)

      groovyModuleBuilder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
          compositionSettings?.addLibraries(rootModel, mutableListOf(), librariesContainer)
        }
      })

      groovyModuleBuilder.commit(project)
      if (addSampleCode) {
        openSampleCodeInEditorLater(project, contentRoot)
      }

      logSdkFinished(sdk)
    }

    init {
      sdkProperty.afterChange { logSdkChanged(it) }
      addSampleCodeProperty.afterChange { logAddSampleCodeChanged() }
      moduleNameProperty.afterChange { logModuleNameChanged() }
      contentRootProperty.afterChange { logContentRootChanged() }
      moduleFileLocationProperty.afterChange { logModuleFileLocationChanged() }
    }

    private fun openSampleCodeInEditorLater(project: Project, contentEntryPath: String) {
      StartupManager.getInstance(project).runAfterOpened {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("$contentEntryPath/src/Main.groovy")
        if (file != null) {
          runReadAction {
            PsiManager.getInstance(project).findFile(file)?.let {
              ApplicationManager.getApplication().invokeLater { EditorHelper.openInEditor(it) }
            }
          }
        }
      }
    }

    private fun reportFeature() {
      val (distributionType, version) = when (val distr = distribution) {
        is FrameworkLibraryDistributionInfo -> GroovyNewProjectWizardUsageCollector.DistributionType.MAVEN to distr.version.versionString
        is LocalDistributionInfo -> GroovyNewProjectWizardUsageCollector.DistributionType.LOCAL to GroovyConfigUtils.getInstance().getSDKVersionOrNull(
          distr.path)
        else -> return
      }
      if (version == null) {
        return
      }
      GroovyNewProjectWizardUsageCollector.logGroovyLibrarySelected(context, distributionType, version)
    }

    private fun createCompositionSettings(project: Project, container: LibrariesContainer): LibraryCompositionSettings? {
      val libraryDescription = GroovyLibraryDescription()
      val versionFilter = FrameworkLibraryVersionFilter.ALL
      val pathProvider = { project.basePath ?: "./" }
      when (val distribution = distribution) {
        is FrameworkLibraryDistributionInfo -> {
          val allVersions = listOf(distribution.version)
          return LibraryCompositionSettings(libraryDescription, pathProvider, versionFilter, allVersions).apply {
            setDownloadLibraries(true)
            downloadFiles(null)
          }
        }
        is LocalDistributionInfo -> {
          val settings = LibraryCompositionSettings(libraryDescription, pathProvider, versionFilter, emptyList())
          val virtualFile = VfsUtil.findFile(Path.of(distribution.path), false) ?: return settings
          val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
          val activeWindow = keyboardFocusManager.activeWindow
          val newLibraryConfiguration = libraryDescription.createLibraryConfiguration(activeWindow, virtualFile) ?: return settings
          val libraryEditor = NewLibraryEditor(newLibraryConfiguration.libraryType, newLibraryConfiguration.properties)
          libraryEditor.name = container.suggestUniqueLibraryName(newLibraryConfiguration.defaultLibraryName)
          newLibraryConfiguration.addRoots(libraryEditor)
          settings.setNewLibraryEditor(libraryEditor)
          return settings
        }
        else -> return null
      }
    }

    private class FrameworkLibraryDistributionInfo(val version: FrameworkLibraryVersion) : AbstractDistributionInfo() {
      override val name: String = version.versionString
      override val description: String? = null
    }
  }
}