// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private val LOG = logger<PlatformProjectOpenProcessor>()

class PlatformProjectOpenProcessor : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  enum class Option {
    FORCE_NEW_FRAME,
    @Suppress("unused")
    TEMP_PROJECT
  }

  companion object {
    @JvmField
    val PROJECT_OPENED_BY_PLATFORM_PROCESSOR = Key.create<Boolean>("PROJECT_OPENED_BY_PLATFORM_PROCESSOR")

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
    @Suppress("UNUSED_PARAMETER")
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    @Deprecated("Use {@link #doOpenProject(Path, OpenProjectTask)} ")
    fun doOpenProject(virtualFile: VirtualFile,
                      projectToClose: Project?,
                      forceOpenInNewFrame: Boolean,
                      line: Int,
                      callback: ProjectOpenedCallback?,
                      isReopen: Boolean): Project? {
      val options = OpenProjectTask(forceOpenInNewFrame = forceOpenInNewFrame, projectToClose = projectToClose, line = line)
      return doOpenProject(Paths.get(virtualFile.path), options)
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    @Deprecated("Use {@link #doOpenProject(Path, OpenProjectTask)} ")
    fun doOpenProject(virtualFile: VirtualFile,
                      projectToClose: Project?,
                      line: Int,
                      callback: ProjectOpenedCallback?,
                      options: EnumSet<Option>): Project? {
      val openProjectOptions = OpenProjectTask(forceOpenInNewFrame = options.contains(Option.FORCE_NEW_FRAME), projectToClose = projectToClose, callback = callback, line = line)
      return doOpenProject(Paths.get(virtualFile.path), openProjectOptions)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun createTempProjectAndOpenFile(file: Path, options: OpenProjectTask): Project? {
      val dummyProjectName = file.fileName.toString()
      val baseDir = FileUtilRt.createTempDirectory(dummyProjectName, null, true).toPath()
      val copy = options.copy(isNewProject = true, projectName = dummyProjectName, isDummyProject = true, contentRoot = file)
      val project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(baseDir, copy) ?: return null
      openFileFromCommandLine(project, file, copy.line, copy.column)
      return project
    }

    @ApiStatus.Internal
    @JvmStatic
    fun doOpenProject(file: Path, options: OpenProjectTask): Project? {
      LOG.info("Opening $file")
      var baseDir = file
      if (!Files.isDirectory(file)) {
        if (LightEditUtil.openFile(file)) {
          return LightEditUtil.getProject()
        }

        var baseDirCandidate = file.parent
        while (baseDirCandidate != null && !Files.exists(baseDirCandidate.resolve(Project.DIRECTORY_STORE_FOLDER))) {
          baseDirCandidate = baseDirCandidate.parent
        }

        // no reasonable directory -> create new temp one or use parent
        if (baseDirCandidate == null) {
          LOG.info("No project directory found")
          if (Registry.`is`("ide.open.file.in.temp.project.dir")) {
            return createTempProjectAndOpenFile(file, options)
          }

          baseDir = file.parent
          options.isNewProject = !Files.isDirectory(baseDir.resolve(Project.DIRECTORY_STORE_FOLDER))
        }
        else {
          baseDir = baseDirCandidate
          LOG.info("Project directory found: $baseDir")
        }
      }

      val project = ProjectManagerEx.getInstanceEx().loadAndOpenProject(baseDir, if (baseDir == file) options else options.copy(contentRoot = file))
      if (project != null && file != baseDir && !Files.isDirectory(file)) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    @ApiStatus.Internal
    @JvmStatic
    @Deprecated(message = "If project base dir differs from project store base dir, specify it as contentRoot in the options", level = DeprecationLevel.ERROR)
    fun openExistingProject(file: Path, projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
      if (file == projectStoreBaseDir) {
        return ProjectManagerEx.getInstanceEx().loadAndOpenProject(projectStoreBaseDir, options)
      }
      else {
        return ProjectManagerEx.getInstanceEx().loadAndOpenProject(projectStoreBaseDir, options.copy(contentRoot = file))
      }
    }

    @JvmStatic
    fun runDirectoryProjectConfigurators(baseDir: Path, project: Project, newProject: Boolean): Module {
      val moduleRef = Ref<Module>()
      val virtualFile = ProjectUtil.getFileAndRefresh(baseDir)!!
      DirectoryProjectConfigurator.EP_NAME.forEachExtensionSafe { configurator: DirectoryProjectConfigurator ->
        configurator.configureProject(project, virtualFile, moduleRef, newProject)
      }
      return moduleRef.get()
    }

    @JvmStatic
    fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
      return ProjectAttachProcessor.EP_NAME.findFirstSafe { processor ->
        processor.attachToProject(project, projectDir, callback)
      } != null
    }
  }

  override fun canOpenProject(file: VirtualFile) = file.isDirectory

  override fun isProjectFile(file: VirtualFile) = false

  override fun lookForProjectsInDirectory() = false

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    val options = OpenProjectTask(forceOpenInNewFrame = forceOpenInNewFrame, projectToClose = projectToClose)
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // doesn't make sense to use default project in tests for heavy projects
      options.useDefaultProjectAsTemplate = false
    }
    val baseDir = Paths.get(virtualFile.path)
    options.isNewProject = !ProjectUtil.isValidProjectPath(baseDir)
    return doOpenProject(baseDir, options)
  }

  override fun openProjectAndFile(virtualFile: VirtualFile, line: Int, column: Int, tempProject: Boolean): Project? {
    // force open in a new frame if temp project
    val file = Paths.get(virtualFile.path)
    if (tempProject) {
      return createTempProjectAndOpenFile(file, OpenProjectTask(forceOpenInNewFrame = true, line = line, column = column))
    }
    else {
      return doOpenProject(file, OpenProjectTask(line = line, column = column))
    }
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

      val virtualFile = ProjectUtil.getFileAndRefresh(file) ?: return@Runnable
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