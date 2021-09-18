// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.ide.wizard.NewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardLanguageStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.roots.ui.distribution.*
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox.NoDistributionInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.plugins.groovy.GroovyBundle
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.nio.file.Path
import javax.swing.SwingUtilities

class GroovyNewProjectWizard : NewProjectWizard {
  override val name: String = "Groovy"

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parent: NewProjectWizardLanguageStep) : AbstractNewProjectWizardChildStep<NewProjectWizardLanguageStep>(parent) {
    val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
    val distributionsProperty = propertyGraph.graphProperty<DistributionInfo> { NoDistributionInfo }

    var javaSdk by sdkProperty
    var distribution by distributionsProperty

    override fun setupUI(builder: Panel) {
      with(builder) {
        row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
          val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
          sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
            .columns(COLUMNS_MEDIUM)
        }.bottomGap(BottomGap.SMALL)
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
          comboBox.comboBoxActionName = GroovyBundle.message("dialog.title.specify.groovy.sdk")
          comboBox.noDistributionName = GroovyBundle.message("combo.box.null.value.placeholder")
          val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
          if (pathToGroovyHome != null) {
            comboBox.addItemIfNotExists(LocalDistributionInfo(pathToGroovyHome.path))
          }
          downloadableLibraryDescription.fetchVersions(object : FileSetVersionsCallback<FrameworkLibraryVersion>() {
            override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
              versions.sortedWith(::moveUnstablesToTheEnd)
                .map(::FrameworkLibraryDistributionInfo)
                .forEach(comboBox::addItemIfNotExists)
              comboBox.noDistributionName = ProjectBundle.message("sdk.missing.item")
            }
          })
          cell(comboBox)
            .bindItem(distributionsProperty)
            .columns(COLUMNS_MEDIUM)
        }.bottomGap(BottomGap.SMALL)
      }
    }

    private fun moveUnstablesToTheEnd(left: FrameworkLibraryVersion, right: FrameworkLibraryVersion): Int {
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
        contentEntryPath = parentStep.projectPath.systemIndependentPath
        name = parentStep.name
        moduleJdk = javaSdk
      }

      groovyModuleBuilder.addModuleConfigurationUpdater(object : ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
          val librariesContainer = LibrariesContainerFactory.createContainer(project)
          val compositionSettings = createCompositionSettings(project, librariesContainer)
          compositionSettings.addLibraries(rootModel, mutableListOf(), librariesContainer)
        }
      })

      groovyModuleBuilder.commit(project)
    }

    private fun createCompositionSettings(project: Project, container: LibrariesContainer): LibraryCompositionSettings {
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
          val settings = LibraryCompositionSettings(libraryDescription, pathProvider, versionFilter, listOf(null))
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
        else -> throw NoWhenBranchMatchedException(distribution.javaClass.toString())
      }
    }

    private class FrameworkLibraryDistributionInfo(val version: FrameworkLibraryVersion) : AbstractDistributionInfo() {
      override val name: String = version.versionString
      override val description: String? = null
    }
  }
}
