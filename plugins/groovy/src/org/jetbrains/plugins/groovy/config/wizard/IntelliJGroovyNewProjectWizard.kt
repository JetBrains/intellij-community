// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription
import java.awt.KeyboardFocusManager
import java.nio.file.Path
import java.nio.file.Paths

private class IntelliJGroovyNewProjectWizard : BuildSystemGroovyNewProjectWizard {

  override val name = INTELLIJ

  override val ordinal = 0

  override fun createStep(parent: GroovyNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: GroovyNewProjectWizard.Step) :
    IntelliJNewProjectWizardStep<GroovyNewProjectWizard.Step>(parent),
    BuildSystemGroovyNewProjectWizardData by parent {

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupGroovySdkUI(builder)
      setupSampleCodeUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupModuleNameUI(builder)
      setupModuleContentRootUI(builder)
      setupModuleFileLocationUI(builder)
    }

    override fun setupProject(project: Project) {
      val groovyModuleBuilder = GroovyAwareModuleBuilder().apply {
        val contentRoot = FileUtil.toSystemDependentName(contentRoot)
        contentEntryPath = contentRoot
        name = moduleName
        moduleJdk = sdk
        sdkDownloadTask?.let { task ->
          val incompleteSdk = project.service<JdkDownloadService>().setupInstallableSdk(task)
          if (context.isCreatingNewProject) ApplicationManager.getApplication().runWriteAction {
            ProjectRootManager.getInstance(project).projectSdk = incompleteSdk
          }
          moduleJdk = incompleteSdk
        }
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

      setupProjectFromBuilder(project, groovyModuleBuilder)
      if (addSampleCode) {
        openSampleCodeInEditorLater(project, contentRoot)
      }
      sdkDownloadTask?.let { project.service<JdkDownloadService>().downloadSdk(groovyModuleBuilder.moduleJdk) }
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
      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
      }
    }
  }
}