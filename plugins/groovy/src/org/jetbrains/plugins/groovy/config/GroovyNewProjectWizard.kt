// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.CommonBundle
import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.wizard.LanguageNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardLanguageStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.distribution.*
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox.Item
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import org.jetbrains.plugins.groovy.GroovyBundle
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities

class GroovyNewProjectWizard : LanguageNewProjectWizard {
  override val name: String = "Groovy"

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parentStep: NewProjectWizardLanguageStep) : IntelliJNewProjectWizardStep<NewProjectWizardLanguageStep>(parentStep) {
    val distributionsProperty = propertyGraph.graphProperty<DistributionInfo?> { null }

    var distribution by distributionsProperty

    override fun Panel.customOptions() {
        row(GroovyBundle.message("label.groovy.sdk")) {
          val groovyLibraryDescription = GroovyLibraryDescription()
          val groovyLibraryType = LibraryType.EP_NAME.findExtensionOrFail(GroovyDownloadableLibraryType::class.java)
          val downloadableLibraryDescription = groovyLibraryType.libraryDescription
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
          downloadableLibraryDescription.fetchVersions(object : FileSetVersionsCallback<FrameworkLibraryVersion>() {
            override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
              versions.sortedWith(::moveUnstableVersionToTheEnd)
                .map(::FrameworkLibraryDistributionInfo)
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
              { it?.let(Item::Distribution) ?: Item.NoDistribution },
              { (it as? Item.Distribution)?.info }
            ))
            .validationOnInput { validateGroovySdkPath(it) }
            .columns(COLUMNS_MEDIUM)
        }.bottomGap(BottomGap.SMALL)
    }

    private fun ValidationInfoBuilder.validateGroovySdkPath(comboBox: DistributionComboBox) : ValidationInfo? {
      val localDistribution = comboBox.selectedDistribution as? LocalDistributionInfo ?: return null
      val path = localDistribution.path
      if (path.isEmpty()) {
        return error(GroovyBundle.message("dialog.title.validation.path.should.not.be.empty"))
      }
      else if (isInvalidSdk(localDistribution)) {
        return error(GroovyBundle.message("dialog.title.validation.path.does.not.contain.groovy.sdk"))
      } else {
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
            GroovyBundle.message("dialog.title.validation.directory.you.specified.does.not.contain.groovy.sdk.do.you.want.to.create.project.with.this.configuration"),
            GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.title"),
            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
            Messages.getWarningIcon()) != Messages.YES) {
          return error(GroovyBundle.message("dialog.title.validation.invalid.sdk.specified.error"))
        }
      }
      return null
    }

    private fun isBlankDistribution(distribution: DistributionInfo?) : Boolean {
      return distribution == null || (distribution is LocalDistributionInfo &&
                                      distribution.path == "")
    }

    private fun isInvalidSdk(distribution: DistributionInfo?) : Boolean {
      return distribution == null || (distribution is LocalDistributionInfo &&
                                      GroovyConfigUtils.getInstance().getSDKVersionOrNull(distribution.path) == null)
    }

    private fun moveUnstableVersionToTheEnd(left: FrameworkLibraryVersion, right: FrameworkLibraryVersion): Int {
      val leftVersion = left.versionString
      val rightVersion = right.versionString
      val leftUnstable = GroovyConfigUtils.isUnstable(leftVersion)
      val rightUnstable = GroovyConfigUtils.isUnstable(rightVersion)
      return when {
        leftUnstable == rightUnstable -> -GroovyConfigUtils.compareSdkVersions(leftVersion, rightVersion)
        leftUnstable -> 1
        else -> -1
      }
    }

    override fun setupProject(project: Project) {
      val groovyModuleBuilder = GroovyAwareModuleBuilder().apply {
        contentEntryPath = FileUtil.toSystemDependentName(contentRoot)
        name = moduleName
        moduleJdk = sdk
        val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)
        moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
      }

      groovyModuleBuilder.addModuleConfigurationUpdater(object : ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
          val librariesContainer = LibrariesContainerFactory.createContainer(project)
          val compositionSettings = createCompositionSettings(project, librariesContainer)
          compositionSettings?.addLibraries(rootModel, mutableListOf(), librariesContainer)
        }
      })

      groovyModuleBuilder.commit(project)
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
          val keyboardFocusManager = getCurrentKeyboardFocusManager()
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
