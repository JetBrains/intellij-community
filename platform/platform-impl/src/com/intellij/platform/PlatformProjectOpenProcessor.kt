// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.*
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.checkTrustedState
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CancellationException

private val LOG = logger<PlatformProjectOpenProcessor>()
private val EP_NAME = ExtensionPointName<DirectoryProjectConfigurator>("com.intellij.directoryProjectConfigurator")

@Internal
val PROJECT_OPENED_BY_PLATFORM_PROCESSOR: Key<Boolean> = Key.create("PROJECT_OPENED_BY_PLATFORM_PROCESSOR")
private val PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR: Key<Boolean> = Key.create("PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR")

@Internal
val PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES: Key<Boolean> = Key.create("PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES")

internal val PROJECT_NEWLY_OPENED: Key<Boolean> = Key.create("PROJECT_NEWLY_OPENED")

@Internal
fun isConfiguredByPlatformProcessor(project: Project): Boolean = project.getUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR) == true

internal fun isLoadedFromCacheButHasNoModules(project: Project): Boolean {
  return project.getUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES) == true
}

class PlatformProjectOpenProcessor : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  enum class Option {
    FORCE_NEW_FRAME,

    @Suppress("unused")
    TEMP_PROJECT
  }

  companion object {
    fun isOpenedByPlatformProcessor(project: Project): Boolean = project.getUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR) == true

    fun isNewProject(project: Project): Boolean = project.getUserData(PROJECT_NEWLY_OPENED) == true

    fun isTempProject(project: Project): Boolean = project.service<OpenProjectSettingsService>().state.isLocatedInTempDirectory

    @JvmStatic
    fun getInstance(): PlatformProjectOpenProcessor = EXTENSION_POINT_NAME.findExtensionOrFail(PlatformProjectOpenProcessor::class.java)

    @JvmStatic
    fun getInstanceIfItExists(): PlatformProjectOpenProcessor? {
      return EXTENSION_POINT_NAME.findExtension(PlatformProjectOpenProcessor::class.java)
    }

    @JvmStatic
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use {@link #doOpenProject(Path, OpenProjectTask)}", level = DeprecationLevel.ERROR)
    fun doOpenProject(virtualFile: VirtualFile,
                      projectToClose: Project?,
                      line: Int,
                      callback: ProjectOpenedCallback?,
                      options: EnumSet<Option>): Project? {
      val openProjectOptions = OpenProjectTask {
        forceOpenInNewFrame = Option.FORCE_NEW_FRAME in options
        this.projectToClose = projectToClose
        this.callback = callback
        runConfigurators = callback != null
        this.line = line
      }
      return doOpenProject(virtualFile.toNioPath(), openProjectOptions)
    }

    private fun createTempProjectAndOpenFile(file: Path, options: OpenProjectTask): Project? {
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
      },
      beforeOpen = {
        it.service<OpenProjectSettingsService>().state.isLocatedInTempDirectory = true
        options.beforeOpen?.invoke(it) ?: true
      })
      TrustedPaths.getInstance().setProjectPathTrusted(baseDir, true)
      val project = ProjectManagerEx.getInstanceEx().openProject(baseDir, copy) ?: return null
      openFileFromCommandLine(project = project, file = file, line = copy.line, column = copy.column)
      return project
    }

    internal suspend fun createTempProjectAndOpenFileAsync(file: Path, options: OpenProjectTask): Project? {
      val dummyProjectName = file.fileName.toString()
      val baseDir = Files.createTempDirectory(dummyProjectName)
      val copy = options.copy(
        isNewProject = true,
        projectName = dummyProjectName,
        runConfigurators = true,
        preparedToOpen = { module ->
          // adding content root for chosen (single) file
          val model = readAction { ModuleRootManager.getInstance(module).modifiableModel }
          try {
            val entries = model.contentEntries
            // remove custom content entry created for temp directory
            if (entries.size == 1) {
              model.removeContentEntry(entries.first())
            }
            model.addContentEntry(VfsUtilCore.pathToUrl(file.toString()))

            withContext(Dispatchers.EDT) {
              if (!module.isDisposed) {
                ApplicationManager.getApplication().runWriteAction(model::commit)
              }
            }
          }
          finally {
            if (!model.isDisposed) {
              model.dispose()
            }
          }
        },
        beforeOpen = {
          it.service<OpenProjectSettingsService>().state.isLocatedInTempDirectory = true
          options.beforeOpen?.invoke(it) ?: true
        }
      )
      TrustedPaths.getInstance().setProjectPathTrusted(path = baseDir, value = true)
      val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectStoreBaseDir = baseDir, options = copy) ?: return null
      openFileFromCommandLine(project = project, file = file, line = copy.line, column = copy.column)
      return project
    }

    @Internal
    fun doOpenProject(file: Path, originalOptions: OpenProjectTask): Project? {
      if (Files.isDirectory(file)) {
        val options = runUnderModalProgressIfIsEdt {
          createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, projectToClose = null)
        }
        return ProjectManagerEx.getInstanceEx().openProject(file, options)
      }

      var options = originalOptions
      if (LightEditService.getInstance() != null && LightEditService.getInstance().isForceOpenInLightEditMode) {
        LightEditService.getInstance().openFile(file, false)?.let {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
          return it
        }
      }

      val storePathManager = ProjectStorePathManager.getInstance()
      var baseDirCandidate = file.parent
      while (baseDirCandidate != null && !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDirCandidate)) {
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
              FUSProjectHotStartUpMeasurer.lightEditProjectFound()
              return lightEditProject
            }
          }
        }
        if (Registry.`is`("ide.open.file.in.temp.project.dir")) {
          return createTempProjectAndOpenFile(file, options)
        }

        baseDir = file.parent
        options = options.copy(isNewProject = !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDir))
      }
      else {
        baseDir = baseDirCandidate
        LOG.info("Project directory found: $baseDir")
      }

      val project = ProjectManagerEx.getInstanceEx().openProject(
        projectStoreBaseDir = baseDir,
        options = if (baseDir == file) options else options.copy(projectName = file.fileName.toString())
      )
      if (project != null && file != baseDir) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    suspend fun openProjectAsync(file: Path, originalOptions: OpenProjectTask = OpenProjectTask()): Project? {
      LOG.info("Opening $file")

      if (Files.isDirectory(file)) {
        return ProjectManagerEx.getInstanceEx().openProjectAsync(
          projectStoreBaseDir = file,
          options = createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, projectToClose = null),
        )
      }

      var options = originalOptions
      if (LightEditService.getInstance() != null && LightEditService.getInstance().isForceOpenInLightEditMode) {
        LightEditService.getInstance().openFile(file, false)?.let {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
          return it
        }
      }

      var baseDirCandidate = file.parent
      val storePathManager = serviceAsync<ProjectStorePathManager>()
      while (baseDirCandidate != null && !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDirCandidate)) {
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
              FUSProjectHotStartUpMeasurer.lightEditProjectFound()
              return lightEditProject
            }
          }
        }
        if (Registry.`is`("ide.open.file.in.temp.project.dir")) {
          return createTempProjectAndOpenFileAsync(file, options)
        }

        baseDir = file.parent
        options = options.copy(isNewProject = !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDir))
      }
      else {
        baseDir = baseDirCandidate
        LOG.info("Project directory found: $baseDir")
      }

      val project = ProjectManagerEx.getInstanceEx().openProjectAsync(
        projectStoreBaseDir = baseDir,
        options = if (baseDir == file) options else options.copy(projectName = file.fileName.toString())
      )
      if (project != null && file != baseDir) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    suspend fun runDirectoryProjectConfigurators(baseDir: Path, project: Project, newProject: Boolean): Module? {
      project.putUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR, true)

      val moduleRef = Ref<Module>()

      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir)!!
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          virtualFile.refresh(false, false)
        }
      }

      for (configurator in EP_NAME.lazySequence()) {
        try {
          if (configurator is DirectoryProjectConfigurator.AsyncDirectoryProjectConfigurator) {
            configurator.configure(project, virtualFile, moduleRef, newProject)
          }
          else if (configurator.isEdtRequired) {
            withContext(Dispatchers.EDT) {
              SlowOperations.knownIssue("IDEA-319905, EA-808639").use {
                configurator.configureProject(project, virtualFile, moduleRef, newProject)
              }
            }
          }
          else {
            configurator.configureProject(project, virtualFile, moduleRef, newProject)
          }
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }

      return moduleRef.get()
    }

    @RequiresEdt
    @Internal
    fun attachToProject(project: Project, projectDir: Path, callback: ProjectOpenedCallback?): Boolean {
      return runWithModalProgressBlocking(project, "") {
        attachToProjectAsync(projectToClose = project, projectDir = projectDir, callback = callback)
      }
    }

    /**
     * If a project file in IDEA format (`.idea` directory or `.ipr` file) exists, opens it and runs configurators if no modules.
     * Otherwise, creates a new project using the default project template and runs configurators (something that creates a module)
     * (at the moment of creation project file in IDEA format will be removed if any).
     * <p>
     * This method must be not used in tests.
     *
     * See `OpenProjectTest`.
     */
    @Internal
    @JvmStatic
    suspend fun createOptionsToOpenDotIdeaOrCreateNewIfNotExists(projectDir: Path, projectToClose: Project?): OpenProjectTask {
      return OpenProjectTask {
        runConfigurators = true
        isNewProject = !ProjectUtil.isValidProjectPath(projectDir)
        this.projectToClose = projectToClose
        useDefaultProjectAsTemplate = true
      }
    }

    @Internal
    suspend fun OpenProjectTaskBuilder.configureToOpenDotIdeaOrCreateNewIfNotExists(projectDir: Path, projectToClose: Project?) {
      runConfigurators = true
      isNewProject = !ProjectUtil.isValidProjectPath(projectDir)
      this.projectToClose = projectToClose
      useDefaultProjectAsTemplate = true
    }
  }

  override fun canOpenProject(file: VirtualFile): Boolean = file.isDirectory

  override fun isProjectFile(file: VirtualFile): Boolean = false

  override fun lookForProjectsInDirectory(): Boolean = false

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    val baseDir = virtualFile.toNioPath()
    val options = runUnderModalProgressIfIsEdt {
      createOptionsToOpenDotIdeaOrCreateNewIfNotExists(baseDir, projectToClose)
    }.copy(forceOpenInNewFrame = forceOpenInNewFrame)
    return doOpenProject(baseDir, options)
  }

  // force open in a new frame if temp project
  override suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask): Project? {
    if (tempProject) {
      return createTempProjectAndOpenFile(file = file, options = options.copy(forceOpenInNewFrame = true))
    }
    else {
      return openProjectAsync(file = file, originalOptions = options)
    }
  }

  override val name: String
    get() = "text editor"
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
    }, ModalityState.nonModal(), project.disposed)
  }
}

@Internal
suspend fun attachToProjectAsync(
  projectToClose: Project,
  projectDir: Path,
  processor: ProjectAttachProcessor? = null,
  callback: ProjectOpenedCallback? = null,
  beforeOpen: (suspend (Project) -> Boolean)? = null
): Boolean {
  if (!checkTrustedState(projectDir)) {
    return false
  }
  if (processor != null) {
    return attachImpl(processor, projectToClose, projectDir, callback, beforeOpen)
  }
  for (attachProcessor in ProjectAttachProcessor.EP_NAME.lazySequence()) {
    if (attachImpl(attachProcessor, projectToClose, projectDir, callback, beforeOpen)) {
      return true
    }
  }
  return false
}

private suspend fun attachImpl(
  attachProcessor: ProjectAttachProcessor,
  projectToClose: Project,
  projectDir: Path,
  callback: ProjectOpenedCallback?,
  beforeOpen: (suspend (Project) -> Boolean)?,
): Boolean {
  return runCatching {
    attachProcessor.attachToProjectAsync(projectToClose, projectDir, callback, beforeOpen)
  }.getOrLogException(LOG) == true
}
