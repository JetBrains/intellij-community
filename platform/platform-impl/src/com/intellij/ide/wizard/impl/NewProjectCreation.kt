// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NewProjectCreation")

package com.intellij.ide.wizard.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.focusProjectWindow
import com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.util.projectWizard.ProjectBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.projectImport.ProjectOpenedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<NewProjectCreationHooks>()

/**
 * The language-specific parts of [createProjectFromBuilder]. The Java new project wizard uses them for the compiler output,
 * the project JDK and the Project Structure dialog.
 */
@ApiStatus.Internal
interface NewProjectCreationHooks {
  /** Runs after the project is created and before [ProjectBuilder.commit]. */
  suspend fun beforeCommit(project: Project) {}

  /** Runs after [ProjectBuilder.commit]. */
  suspend fun afterCommit(project: Project) {}

  /** Runs on the EDT after the startup activities of the opened project. */
  fun afterOpened(project: Project) {}

  @ApiStatus.Internal
  companion object {
    @JvmField
    val NONE: NewProjectCreationHooks = object : NewProjectCreationHooks {}
  }
}

/**
 * Creates the project of a new project wizard with [projectBuilder] and opens it.
 * Without [projectBuilder], it creates an empty project.
 *
 * The new project attaches to [attachTo] with no question about the window, see [OpenProjectTask.projectToAttach].
 * Without [attachTo], the platform asks the user where the new project opens.
 *
 * @param projectFile the project directory, or the `.ipr` file for [StorageScheme.DEFAULT].
 * @return the new project, or [projectToClose] when [projectBuilder] updates it or fails.
 * It is `null` when a project at [projectFile] is already open.
 */
@ApiStatus.Internal
suspend fun createProjectFromBuilder(
  projectBuilder: ProjectBuilder?,
  projectName: String,
  projectFile: Path,
  storageScheme: StorageScheme,
  projectToClose: Project?,
  attachTo: Project?,
  hooks: NewProjectCreationHooks = NewProjectCreationHooks.NONE,
): Project? {
  val projectManager = (serviceAsync<ProjectManager>() as ProjectManagerEx)
  for (p in projectManager.getOpenProjects()) {
    if (ProjectUtil.isSameProject(projectFile, p)) {
      focusProjectWindow(project = p, stealFocusIfAppInactive = false)
      return null
    }
  }

  LOG.debug { "builder $projectBuilder" }
  try {
    val projectDir = if (storageScheme == StorageScheme.DEFAULT) {
      projectFile.parent ?: throw IOException("Cannot create project in '$projectFile': no parent file exists")
    }
    else {
      projectFile
    }
    Files.createDirectories(projectDir)
    val newProject: Project? = if (projectBuilder == null || !projectBuilder.isUpdate) {
      if (projectBuilder == null) {
        projectManager.newProject(projectFile, OpenProjectTask {
          isNewProject = true
          useDefaultProjectAsTemplate = true
          this.projectName = projectName
        })
      }
      else {
        try {
          projectBuilder.createProject(projectName, projectFile.toString())
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.error(e)
          null
        }
      }
    }
    else {
      projectToClose
    }

    if (newProject == null) {
      return projectToClose
    }

    hooks.beforeCommit(newProject)

    if (projectBuilder != null) {
      // validate can require a project on disk
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        SaveAndSyncHandler.getInstance().withDisabledAutoSave {
          saveSettings(componentManager = newProject, forceSavingAllSettings = true)
        }
      }
      if (!projectBuilder.validate(projectToClose, newProject)) {
        return projectToClose
      }

      withContext(Dispatchers.EDT) {
        projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
      }
    }

    hooks.afterCommit(newProject)

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      newProject.serviceAsync<StartupManager>().runAfterOpened {
        // ensure the dialog is shown after all startup activities are done
        ApplicationManager.getApplication().invokeLater({
          hooks.afterOpened(newProject)
          ApplicationManager.getApplication().invokeLater(
            {
              ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
            }, ModalityState.nonModal(), newProject.disposed)
        }, ModalityState.nonModal(), newProject.disposed)
      }
    }

    if (newProject !== projectToClose) {
      updateLastProjectLocation(projectFile)
      val options = OpenProjectTask {
        project = newProject
        this.projectName = projectFile.fileName.toString()
        projectRootDir = projectDir
        // the wizard can outlive the frame that started it
        if (attachTo != null && !attachTo.isDisposed) {
          projectToAttach = attachTo
        }
        callback = ProjectOpenedCallback { openedProject, _ ->
          if (openedProject != newProject) { // project attached to workspace
            VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectDir)?.let { dir ->
              ApplicationManager.getApplication().invokeLater {
                projectBuilder?.postCommit(openedProject, dir)
              }
            }
          }
        }
      }

      serviceAsync<TrustedPaths>().setProjectPathTrusted(projectDir, true)
      (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(projectIdentityFile = projectDir, options = options)
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      serviceAsync<SaveAndSyncHandler>().scheduleProjectSave(newProject)
    }
    return newProject
  }
  finally {
    projectBuilder?.cleanup()
  }
}
