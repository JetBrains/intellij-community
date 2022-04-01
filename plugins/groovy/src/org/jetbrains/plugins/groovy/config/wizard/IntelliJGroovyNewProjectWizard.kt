// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logContentRootChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleFileLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logModuleNameChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription
import java.awt.KeyboardFocusManager
import java.nio.file.Path
import java.nio.file.Paths

class IntelliJGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {

  override val name: String = "IntelliJ"

  override val ordinal: Int = 0

  override fun createStep(parent: GroovyNewProjectWizard.Step): NewProjectWizardStep = Step(parent).chain(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    override fun Panel.customOptions() {
      row(GroovyBundle.message("label.groovy.sdk")) {
        groovySdkComboBox(context, groovySdkProperty)
      }.bottomGap(BottomGap.SMALL)
    }

    override fun setupProject(project: Project) {
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

    private fun createCompositionSettings(project: Project, container: LibrariesContainer): LibraryCompositionSettings? {
      val libraryDescription = GroovyLibraryDescription()
      val versionFilter = FrameworkLibraryVersionFilter.ALL
      val pathProvider = { project.basePath ?: "./" }
      when (val distribution = groovySdk) {
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
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      if (gitData?.git == true) {
        addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      }
    }
  }
}