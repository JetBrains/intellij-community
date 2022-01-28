// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private val LOG = logger<PlatformProjectOpenProcessor>()
private val EP_NAME = ExtensionPointName<DirectoryProjectConfigurator>("com.intellij.directoryProjectConfigurator")

class PlatformProjectOpenProcessor : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  enum class Option {
    FORCE_NEW_FRAME,
    @Suppress("unused")
    TEMP_PROJECT
  }

  companion object {
    @JvmField
    val PROJECT_OPENED_BY_PLATFORM_PROCESSOR = Key.create<Boolean>("PROJECT_OPENED_BY_PLATFORM_PROCESSOR")

    @JvmField
    val PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR = Key.create<Boolean>("PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR")

    @JvmField
    val PROJECT_NEWLY_OPENED = Key.create<Boolean>("PROJECT_NEWLY_OPENED")

    fun Project.isOpenedByPlatformProcessor(): Boolean =
      getUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR) == true

    fun Project.isConfiguredByPlatformProcessor(): Boolean =
      getUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR) == true

    fun Project.isNewProject(): Boolean =
      getUserData(PROJECT_NEWLY_OPENED) == true

    @JvmStatic
    fun getInstance() = getInstanceIfItExists()!!

    @JvmStatic
    fun getInstanceIfItExists(): PlatformProjectOpenProcessor? {
      for (processor in EXTENSION_POINT_NAME.extensionList) {
        if (processor is PlatformProjectOpenProcessor) {
          return processor
        }
      }
      return null
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    @Deprecated("Use {@link #doOpenProject(Path, OpenProjectTask)} ")
    fun doOpenProject(virtualFile: VirtualFile,
                      projectToClose: Project?,
                      line: Int,
                      callback: ProjectOpenedCallback?,
                      options: EnumSet<Option>): Project? {
      val openProjectOptions = OpenProjectTask(forceOpenInNewFrame = Option.FORCE_NEW_FRAME in options,
                                               projectToClose = projectToClose,
                                               callback = callback,
                                               runConfigurators = callback != null,
                                               line = line)
      return doOpenProject(Path.of(virtualFile.path), openProjectOptions)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun createTempProjectAndOpenFile(file: Path, options: OpenProjectTask): Project? {
      val dummyProjectName = file.fileName.toString()
      val baseDir = FileUtilRt.createTempDirectory(dummyProjectName, null, true).toPath()
      val copy = options.copy(isNewProject = true, projectName = dummyProjectName, runConfigurators = true, preparedToOpen = { module ->
        // adding content root for chosen (single) file
        ModuleRootModificationUtil.updateModel(module) { model ->
          val entries = model.contentEntries
          // remove custom content entry created for temp directory
          if (entries.size == 1) {
            model.removeContentEntry(entries[0])
          }
          model.addContentEntry(VfsUtilCore.pathToUrl(file.toString()))
        }
      })
      val project = ProjectManagerEx.getInstanceEx().openProject(baseDir, copy) ?: return null
      openFileFromCommandLine(project, file, copy.line, copy.column)
      return project
    }

    @ApiStatus.Internal
    @JvmStatic
    fun doOpenProject(file: Path, originalOptions: OpenProjectTask): Project? {
      LOG.info("Opening $file")

      if (Files.isDirectory(file)) {
        return ProjectManagerEx.getInstanceEx().openProject(file, createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, projectToClose = null))
      }

      var options = originalOptions
      if (LightEditService.getInstance() != null) {
        if (LightEditService.getInstance().isForceOpenInLightEditMode) {
          val lightEditProject = LightEditService.getInstance().openFile(file, false)
          if (lightEditProject != null) {
            return lightEditProject
          }
        }
      }

      var baseDirCandidate = file.parent
      while (baseDirCandidate != null && !Files.exists(baseDirCandidate.resolve(Project.DIRECTORY_STORE_FOLDER))) {
        baseDirCandidate = baseDirCandidate.parent
      }

      val baseDir: Path
      // no reasonable directory -> create new temp one or use parent
      if (baseDirCandidate == null) {
        LOG.info("No project directory found")
        if (LightEditService.getInstance() != null) {
          if (LightEditService.getInstance().isLightEditEnabled && !LightEditService.getInstance().isPreferProjectMode) {
            val lightEditProject = LightEditService.getInstance().openFile(file, true)
            if (lightEditProject != null) {
              return lightEditProject
            }
          }
        }
        if (Registry.`is`("ide.open.file.in.temp.project.dir")) {
          return createTempProjectAndOpenFile(file, options)
        }

        baseDir = file.parent
        options = options.copy(isNewProject = !Files.isDirectory(baseDir.resolve(Project.DIRECTORY_STORE_FOLDER)))
      }
      else {
        baseDir = baseDirCandidate
        LOG.info("Project directory found: $baseDir")
      }

      val project = ProjectManagerEx.getInstanceEx().openProject(baseDir, if (baseDir == file) options else options.copy(projectName = file.fileName.toString()))
      if (project != null && file != baseDir) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    @JvmStatic
    fun runDirectoryProjectConfigurators(baseDir: Path, project: Project, newProject: Boolean): Module {
      project.putUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR, true)
      val moduleRef = Ref<Module>()
      val virtualFile = ProjectUtilCore.getFileAndRefresh(baseDir)!!
      EP_NAME.forEachExtensionSafe { configurator ->
        fun task() {
          configurator.configureProject(project, virtualFile, moduleRef, newProject)
        }
        if (configurator.isEdtRequired) {
          ApplicationManager.getApplication().invokeAndWait {
            task()
          }
        }
        else {
          task()
        }
      }
      val module = moduleRef.get()
      if (module == null) {
        LOG.error("No extension configured a module for $baseDir; extensions = ${EP_NAME.extensionList}")
      }
      return module
    }

    @JvmStatic
    fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean =
      ProjectAttachProcessor.EP_NAME.findFirstSafe { processor -> processor.attachToProject(project, projectDir, callback) } != null

    /**
     * If a project file in IDEA format (`.idea` directory or `.ipr` file) exists, opens it and runs configurators if no modules.
     * Otherwise, creates a new project using the default project template and runs configurators (something that creates a module)
     * (at the moment of creation project file in IDEA format will be removed if any).
     * <p>
     * This method must be not used in tests.
     *
     * See `OpenProjectTest`.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun createOptionsToOpenDotIdeaOrCreateNewIfNotExists(projectDir: Path, projectToClose: Project?): OpenProjectTask =
      OpenProjectTask(runConfigurators = true,
                      isNewProject = !ProjectUtilCore.isValidProjectPath(projectDir),
                      projectToClose = projectToClose,
                      isRefreshVfsNeeded = !ApplicationManager.getApplication().isUnitTestMode,  // doesn't make sense to refresh
                      useDefaultProjectAsTemplate = true)
  }

  override fun canOpenProject(file: VirtualFile) = file.isDirectory

  override fun isProjectFile(file: VirtualFile) = false

  override fun lookForProjectsInDirectory() = false

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    val baseDir = virtualFile.toNioPath()
    return doOpenProject(baseDir, createOptionsToOpenDotIdeaOrCreateNewIfNotExists(baseDir, projectToClose).copy(forceOpenInNewFrame = forceOpenInNewFrame))
  }

  override fun openProjectAndFile(file: Path, line: Int, column: Int, tempProject: Boolean): Project? =
    // force open in a new frame if temp project
    if (tempProject) {
      createTempProjectAndOpenFile(file, OpenProjectTask(forceOpenInNewFrame = true, line = line, column = column))
    }
    else {
      doOpenProject(file, OpenProjectTask(line = line, column = column))
    }

  @Suppress("HardCodedStringLiteral")
  override fun getName() = "text editor"
}

private fun openFileFromCommandLine(project: Project, file: Path, line: Int, column: Int) {
  StartupManager.getInstance(project).runAfterOpened {
    ApplicationManager.getApplication().invokeLater(Runnable {
      if (project.isDisposed || !Files.exists(file)) {
        return@Runnable
      }

      val virtualFile = ProjectUtilCore.getFileAndRefresh(file) ?: return@Runnable
      val navigatable = if (line > 0) {
        OpenFileDescriptor(project, virtualFile, line - 1, column.coerceAtLeast(0))
      }
      else {
        PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, -1)
      }
      navigatable.navigate(true)
    }, ModalityState.NON_MODAL, project.disposed)
  }
}
