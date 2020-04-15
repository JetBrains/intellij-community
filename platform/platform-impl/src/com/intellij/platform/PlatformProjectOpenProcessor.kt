// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.ui.IdeUICustomization
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
      val baseDir = FileUtil.createTempDirectory(dummyProjectName, null, true).toPath()
      val copy = options.copy(isNewProject = true, projectName = dummyProjectName, isDummyProject = true)
      val project = openExistingProject(file, baseDir, copy) ?: return null
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

      SaveAndSyncHandler.getInstance().disableAutoSave().use {
        val project = openExistingProject(file, baseDir, options)
        if (project != null && file !== baseDir && !Files.isDirectory(file)) {
          openFileFromCommandLine(project, file, options.line, options.column)
        }
        return project
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun openExistingProject(file: Path, projectDir: Path?, options: OpenProjectTask): Project? {
      if (options.project != null) {
        val projectManager = ProjectManagerEx.getInstanceExIfCreated()
        if (projectManager != null && projectManager.isProjectOpened(options.project)) {
          return null
        }
      }

      val activity = StartUpMeasurer.startMainActivity("project opening preparation")
      if (!options.forceOpenInNewFrame) {
        val openProjects = ProjectUtil.getOpenProjects()
        if (openProjects.isNotEmpty()) {
          var projectToClose = options.projectToClose
          if (projectToClose == null) {
            // if several projects are opened, ask to reuse not last opened project frame, but last focused (to avoid focus switching)
            val lastFocusedFrame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
            projectToClose = lastFocusedFrame?.project
            if (projectToClose == null || projectToClose is LightEditCompatible) {
              projectToClose = openProjects[openProjects.size - 1]
            }
          }
          if (checkExistingProjectOnOpen(projectToClose, options.callback, projectDir)) {
            return null
          }
        }
      }

      var result: PrepareProjectResult? = null
      runInAutoSaveDisabledMode {
        val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) ProjectFrameAllocator() else ProjectUiFrameAllocator(options, file)
        val isCompleted = frameAllocator.run {
          activity.end()
          if (options.project == null) {
            result = prepareProject(file, options, projectDir!!)
            if (result?.project == null) {
              frameAllocator.projectNotLoaded(error = null)
              return@run
            }
          }
          else {
            result = PrepareProjectResult(options.project, null)
          }

          val project = result!!.project
          frameAllocator.projectLoaded(project)
          if (ProjectManagerEx.getInstanceEx().openProject(project)) {
            frameAllocator.projectOpened(project)
          }
          else {
            frameAllocator.projectNotLoaded(error = null)
            result = null
          }
        }

        if (!isCompleted) {
          result = null
        }
      }

      val project = result?.project
      if (project == null) {
        if (options.showWelcomeScreen) {
          WelcomeFrame.showIfNoProjectOpened()
        }
        return null
      }
      if (options.callback != null) {
        var module = result?.module
        if (module == null) {
          module = ModuleManager.getInstance(project).modules[0]
        }
        options.callback!!.projectOpened(project, module)
      }
      return project
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @JvmStatic
    @Deprecated("Use {@link #runDirectoryProjectConfigurators(Path, Project, boolean)}")
    fun runDirectoryProjectConfigurators(baseDir: VirtualFile, project: Project): Module {
      return runDirectoryProjectConfigurators(Paths.get(baseDir.path), project, false)
    }

    @JvmStatic
    fun runDirectoryProjectConfigurators(baseDir: Path, project: Project, newProject: Boolean): Module {
      val moduleRef = Ref<Module>()
      val virtualFile = ProjectUtil.getFileAndRefresh(baseDir)
      LOG.assertTrue(virtualFile != null)
      DirectoryProjectConfigurator.EP_NAME.forEachExtensionSafe { configurator: DirectoryProjectConfigurator ->
        configurator.configureProject(project, virtualFile!!, moduleRef, newProject)
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

internal data class PrepareProjectResult(val project: Project, val module: Module?)

private fun prepareProject(file: Path, options: OpenProjectTask, baseDir: Path): PrepareProjectResult? {
  val project: Project?
  val isNewProject = options.isNewProject
  if (isNewProject) {
    val projectName = options.projectName ?: baseDir.fileName.toString()
    project = ProjectManagerEx.getInstanceEx().newProject(baseDir, projectName, options)
  }
  else {
    val indicator = ProgressManager.getInstance().progressIndicator
    indicator?.text = IdeUICustomization.getInstance().projectMessage("progress.text.project.checking.configuration")
    project = convertAndLoadProject(baseDir, options)
    indicator?.text = ""
  }

  if (project == null) {
    return null
  }

  val module = configureNewProject(project, baseDir, file, options.isDummyProject, isNewProject)
  if (isNewProject) {
    project.save()
  }
  return PrepareProjectResult(project, module)
}

private fun configureNewProject(project: Project, baseDir: Path, dummyFileContentRoot: Path, dummyProject: Boolean, newProject: Boolean): Module? {
  val runConfigurators = newProject || ModuleManager.getInstance(project).modules.isEmpty()
  var module: Module? = null
  if (runConfigurators) {
    ApplicationManager.getApplication().invokeAndWait {
      module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(baseDir, project, newProject)
    }
  }

  if (runConfigurators && dummyProject) {
    // add content root for chosen (single) file
    ModuleRootModificationUtil.updateModel(module!!) { model ->
      val entries = model.contentEntries
      // remove custom content entry created for temp directory
      if (entries.size == 1) {
        model.removeContentEntry(entries[0])
      }
      model.addContentEntry(VfsUtilCore.pathToUrl(dummyFileContentRoot.toString()))
    }
  }
  return module
}

private fun checkExistingProjectOnOpen(projectToClose: Project, callback: ProjectOpenedCallback?, projectDir: Path?): Boolean {
  val settings = GeneralSettings.getInstance()
  val isValidProject = projectDir != null && ProjectUtil.isValidProjectPath(projectDir)
  if (projectDir != null && ProjectAttachProcessor.canAttachToProject() &&
      (!isValidProject || settings.confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK)) {
    val exitCode = ProjectUtil.confirmOpenOrAttachProject()
    if (exitCode == -1) {
      return true
    }
    else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
      if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
        return true
      }
    }
    else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) {
      if (PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, callback)) {
        return true
      }
    }
    // process all pending events that can interrupt focus flow
    // todo this can be removed after taming the focus beast
    IdeEventQueue.getInstance().flushQueue()
  }
  else {
    val exitCode = ProjectUtil.confirmOpenNewProject(false)
    if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
      if (!ProjectManagerEx.getInstanceEx().closeAndDispose(projectToClose)) {
        return true
      }
    }
    else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
      // not in a new window
      return true
    }
  }
  return false
}

private fun openFileFromCommandLine(project: Project, file: Path, line: Int, column: Int) {
  StartupManager.getInstance(project).registerPostStartupDumbAwareActivity {
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

private fun convertAndLoadProject(path: Path, options: OpenProjectTask): Project? {
  val conversionResult = runActivity("project conversion", category = ActivityCategory.MAIN) {
    ConversionService.getInstance().convert(path)
  }
  if (conversionResult.openingIsCanceled()) {
    return null
  }

  val project = ProjectManagerImpl.doCreateProject(options.projectName, path)
  try {
    ProjectManagerImpl.initProject(path, project, /* isRefreshVfsNeeded = */ true, null, ProgressManager.getInstance().progressIndicator)
  }
  catch (e: ProcessCanceledException) {
    return null
  }

  if (!conversionResult.conversionNotNeeded()) {
    StartupManager.getInstance(project).registerPostStartupActivity {
      conversionResult.postStartupActivity(project)
    }
  }
  return project
}
