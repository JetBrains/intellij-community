// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.codeWithMe.ClientId
import com.intellij.configurationStore.ProjectStorePathManager
import com.intellij.ide.CliResult
import com.intellij.ide.CommandLineWaitingManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.OpenProjectTaskBuilder
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.impl.toOpenProjectTask
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.isClaimedByWelcomeScreenProject
import com.intellij.ide.TrustedFiles
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.checkTrustedState
import com.intellij.openapi.project.impl.createFakeModuleForDirectoryProjectConfigurators
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

private val LOG = logger<PlatformProjectOpenProcessor>()
private val EP_NAME = ExtensionPointName<DirectoryProjectConfigurator>("com.intellij.directoryProjectConfigurator")

internal val COMMAND_LINE_FILE_WAIT: Key<CommandLineFileWait> = Key.create("command.line.file.wait")

internal class CommandLineFileWait(val file: VirtualFile, val future: CompletableFuture<CliResult>)

@Internal
val PROJECT_OPENED_BY_PLATFORM_PROCESSOR: Key<Boolean> = Key.create("PROJECT_OPENED_BY_PLATFORM_PROCESSOR")
private val PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR: Key<Boolean> = Key.create("PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR")

@Internal
val PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES: Key<Boolean> = Key.create("PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES")

internal val PROJECT_NEWLY_OPENED: Key<Boolean> = Key.create("PROJECT_NEWLY_OPENED")
internal val PROJECT_NEWLY_CREATED: Key<Boolean> = Key.create("PROJECT_NEWLY_CREATED")

@Internal
fun isConfiguredByPlatformProcessor(project: Project): Boolean = project.getUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR) == true

internal fun isLoadedFromCacheButHasNoModules(project: Project): Boolean {
  return project.getUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES) == true
}

/**
 * A project opens with no module: directory project configurators do not run, and JPS files do not contribute modules.
 * JetBrains Light opens every project this way until it advances to the smart mode, see [IdeProductMode.isMonolithLight].
 */
@Internal
fun isProjectOpenWithoutModule(): Boolean = Registry.`is`("ide.project.open.without.module") || IdeProductMode.isMonolithLight

class PlatformProjectOpenProcessor : ProjectOpenProcessor(), CommandLineProjectOpenProcessor {
  enum class Option {
    FORCE_NEW_FRAME,

    @Suppress("unused")
    TEMP_PROJECT
  }

  companion object {
    @Deprecated("this function is for legacy Java api, do not use it", level = DeprecationLevel.ERROR)
    @JvmStatic
    @JvmOverloads
    @Internal
    fun openProjectLegacyJavaApi(
      virtualFile: VirtualFile,
      projectToClose: Project?,
      forceOpenInNewFrame: Boolean,
      instance: PlatformProjectOpenProcessor? = null,
    ): Project? {
      @Suppress("DEPRECATION") // Function has no thread requirements
      return runUnderModalProgressIfIsEdt { (instance ?: getInstance()).openProjectAsync(virtualFile, projectToClose, forceOpenInNewFrame) }
    }

    fun isOpenedByPlatformProcessor(project: Project): Boolean = project.getUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR) == true

    fun isNewProject(project: Project): Boolean = project.getUserData(PROJECT_NEWLY_OPENED) == true

    @Internal
    fun isNewlyCreatedProject(project: Project): Boolean = project.getUserData(PROJECT_NEWLY_CREATED) == true

    fun isTempProject(project: Project): Boolean = project.service<OpenProjectSettingsService>().state.isLocatedInTempDirectory

    @JvmStatic
    fun getInstance(): PlatformProjectOpenProcessor = EXTENSION_POINT_NAME.findExtensionOrFail(PlatformProjectOpenProcessor::class.java)

    @JvmStatic
    fun getInstanceIfItExists(): PlatformProjectOpenProcessor? {
      return EXTENSION_POINT_NAME.findExtension(PlatformProjectOpenProcessor::class.java)
    }

    private fun createTempProjectOpenTask(
      options: OpenProjectTask,
      dummyProjectName: String,
      file: Path,
    ): OpenProjectTask {
      return OpenProjectTask(options) {
        isNewProject = true
        projectRootDir = file
        createModule = false
        projectName = dummyProjectName
        runConfigurators = false
        runConversionBeforeOpen = false
        beforeOpenTasks = buildList {
          add { project ->
            project.service<OpenProjectSettingsService>().state.isLocatedInTempDirectory = true
            true
          }
          addAll(options.beforeOpenTasks)
        }
        // both callers of this go on to `openFileFromCommandLine`, which is what releases the hold this asks for
        opensFileAfterProjectOpen = true
      }
    }

    private fun createTempProjectAndOpenFile(file: Path, options: OpenProjectTask): Project? {
      val dummyProjectName = file.fileName.toString()
      val baseDir = FileUtilRt.createTempDirectory(dummyProjectName, null, true).toPath()
      val copy = createTempProjectOpenTask(options, dummyProjectName, file)
      TrustedPaths.getInstance().setProjectPathTrusted(baseDir, true)
      val project = ProjectManagerEx.getInstanceEx().openProject(baseDir, copy) ?: return null
      openFileFromCommandLine(project = project, file = file, line = copy.line, column = copy.column)
      return project
    }

    internal suspend fun createTempProjectAndOpenFileAsync(file: Path, options: OpenProjectTask): Project? {
      val dummyProjectName = file.fileName.toString()
      val baseDir = Files.createTempDirectory(dummyProjectName)
      val copy = createTempProjectOpenTask(options, dummyProjectName, file)
      TrustedPaths.getInstance().setProjectPathTrusted(path = baseDir, value = true)
      val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectIdentityFile = baseDir, options = copy) ?: return null
      openFileFromCommandLine(project = project, file = file, line = copy.line, column = copy.column)
      return project
    }

    /**
     * Returns the options that the directory branch of [openProjectAsync] and [doOpenProject] opens the directory [file] with.
     * Unless [originalOptions] say otherwise, the configurators run and [file] is the project root directory.
     *
     * A `beforeInit` task marks the project as opened by this processor, see [isOpenedByPlatformProcessor].
     * A `beforeOpen` task must not set the mark: any `beforeOpen` task makes the project manager initialize the frame
     * only after the whole project preparation.
     * The `beforeOpen` tasks of [originalOptions] run after the mark, so they can still reset it.
     */
    @VisibleForTesting
    internal fun directoryOpenOptions(originalOptions: OpenProjectTask, file: Path): OpenProjectTask {
      return OpenProjectTask(originalOptions) {
        runConfigurators = runConfigurators ?: true
        projectRootDir = projectRootDir ?: file
        beforeInitTasks += { project -> project.putUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR, true) }
      }
    }

    /**
     * Do not use this method. It should be private. Use [ProjectUtil.openOrImport] or [ProjectUtil.openOrImportAsync]
     */
    @Internal
    fun doOpenProject(file: Path, originalOptions: OpenProjectTask): Project? {
      LOG.info("Opening (sync) $file")

      if (originalOptions.createModule && Files.isDirectory(file)) {
        return ProjectManagerEx.getInstanceEx().openProject(file, directoryOpenOptions(originalOptions, file))
      }

      var options = originalOptions
      val claimedByWelcomeScreenProject = isClaimedByWelcomeScreenProject(file)
      val lightEditService = serviceOrNull<LightEditService>()
      if (!claimedByWelcomeScreenProject && lightEditService != null && lightEditService.isForceOpenInLightEditMode()) {
        LightEditService.getInstance().openFile(file, false)?.let {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
          return it
        }
      }

      val storePathManager = ProjectStorePathManager.getInstance()
      var baseDirCandidate = if (Files.isRegularFile(file)) file.parent else null
      while (baseDirCandidate != null && !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDirCandidate)) {
        baseDirCandidate = baseDirCandidate.parent
      }

      val baseDir: Path
      // no reasonable directory -> create new temp one or use parent
      if (baseDirCandidate == null) {
        LOG.info("No project directory found")
        if (!claimedByWelcomeScreenProject && lightEditService != null) {
          if (lightEditService.isLightEditEnabled() && !LightEditService.getInstance().isPreferProjectMode) {
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
        options = OpenProjectTask(options) { isNewProject = !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDir) }
      }
      else {
        baseDir = baseDirCandidate
        LOG.info("Project directory found: $baseDir")
      }

      val project = ProjectManagerEx.getInstanceEx().openProject(
        projectStoreBaseDir = baseDir,
        // the flag is set on exactly the condition under which `openFileFromCommandLine` is called below, so the hold it asks for is
        // always the one that call releases
        options = if (baseDir == file) {
          options
        }
        else {
          OpenProjectTask(options) {
            projectName = file.fileName.toString()
            opensFileAfterProjectOpen = true
          }
        }
      )
      if (project != null && file != baseDir) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    /**
     * Do not use this method. It should be private. Use [ProjectUtil.openOrImportAsync]
     */
    @Internal
    suspend fun openProjectAsync(file: Path, originalOptions: OpenProjectTask): Project? {
      LOG.info("Opening (async) $file")

      val isDirectory = Files.isDirectory(file)
      if (originalOptions.createModule && isDirectory) {
        // todo: this is a shortcut to bypass all the "normal" logic and let project be imported via directory configurators only.
        //  createModule is a rather new thing. Moreover, it is @Internal. It has no external usages yet, and  only a few internal usages.
        //  Effectively, this condition means: if we reached PlatformProjectOpenProcessor with a directory, then use directory configurators
        //   (except a very few very special cases: opening a directory (usually as a temp project) and opening a temp project)
        //   (We also have bazel plugin which sets createModule to false, but at the same time it explicitly sets runConfigurators=true)
        //  Earlier we had `createOptionsToOpenDotIdeaOrCreateNewIfNotExists` instead of `originalOptions.copy`, and it worked as follows:
        //   if we reach this place with a directory - then forget originalOptions, and try to open the folder as exisitng .idea project
        //   or create a new .idea project here and import it via directory condifurators.
        //  Now we try to not reject existing originalOptions and capture the intent (most notably - runConfigurators flag) on the callers side.
        return ProjectManagerEx.getInstanceEx().openProjectAsync(
          projectIdentityFile = file,
          options = directoryOpenOptions(originalOptions, file),
        )
      }

      var options = originalOptions
      val claimedByWelcomeScreenProject = isClaimedByWelcomeScreenProject(file)
      val lightEditService = serviceOrNull<LightEditService>()
      if (!claimedByWelcomeScreenProject && lightEditService != null && lightEditService.isForceOpenInLightEditMode()) {
        LightEditService.getInstance().openFile(file, false)?.let {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
          return it
        }
      }

      var baseDirCandidate = if (Files.isRegularFile(file)) file.parent else null
      val storePathManager = serviceAsync<ProjectStorePathManager>()
      while (baseDirCandidate != null && !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDirCandidate)) {
        baseDirCandidate = baseDirCandidate.parent
      }

      val baseDir: Path
      // no reasonable directory -> create new temp one or use parent
      if (baseDirCandidate == null) {
        LOG.info("No project directory found")
        if (!claimedByWelcomeScreenProject && lightEditService != null) {
          if (lightEditService.isLightEditEnabled() && !LightEditService.getInstance().isPreferProjectMode) {
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
        options = OpenProjectTask(options) { isNewProject = !storePathManager.testStoreDirectoryExistsForProjectRoot(baseDir) }
      }
      else {
        baseDir = baseDirCandidate
        LOG.info("Project directory found: $baseDir")
      }

      val project = ProjectManagerEx.getInstanceEx().openProjectAsync(
        projectIdentityFile = baseDir,
        // as in `doOpenProject`: set on exactly the condition under which `openFileFromCommandLine` is called below
        options = if (baseDir == file) {
          options
        }
        else {
          OpenProjectTask(options) {
            projectName = file.fileName.toString()
            opensFileAfterProjectOpen = true
          }
        }
      )
      if (project != null && file != baseDir) {
        openFileFromCommandLine(project, file, options.line, options.column)
      }
      return project
    }

    @JvmOverloads
    suspend fun runDirectoryProjectConfigurators(
      projectFile: Path,
      project: Project,
      newProject: Boolean,
      createModule: Boolean = true
    ): Module? {
      project.putUserData(PROJECT_CONFIGURED_BY_PLATFORM_PROCESSOR, true)

      val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(projectFile)!!
      RefreshQueue.getInstance().refresh(false, listOf(virtualFile))

      val moduleRef = Ref<Module>()
      if (createModule) {
        moduleRef.set(createFakeModuleForDirectoryProjectConfigurators(projectVirtualFile = virtualFile, moduleManager = project.serviceAsync(), projectFile = projectFile))
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
     * (at the moment of creation a project file in IDEA format will be removed if any).
     * <p>
     * This method must be not used in tests.
     *
     * See `OpenProjectTest`.
     */
    @Internal
    @JvmStatic
    suspend fun createOptionsToOpenDotIdeaOrCreateNewIfNotExists(projectDir: Path, projectToClose: Project?): OpenProjectTask {
      return OpenProjectTask {
        configureToOpenDotIdeaOrCreateNewIfNotExists(projectDir, projectToClose)
      }
    }

    @Internal
    suspend fun OpenProjectTaskBuilder.configureToOpenDotIdeaOrCreateNewIfNotExists(projectDir: Path, projectToClose: Project?) {
      runConfigurators = true
      isNewProject = !ProjectUtil.isValidProjectPath(projectDir)
      this.projectToClose = projectToClose
      useDefaultProjectAsTemplate = true
      projectRootDir = projectDir
    }
  }

  override fun canOpenProject(file: VirtualFile): Boolean {
    return file.isDirectory
  }

  override fun isProjectFile(file: VirtualFile): Boolean {
    return false
  }

  override fun lookForProjectsInDirectory(): Boolean = false

  override suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectOpenOptions: ProjectOpenOptions,
  ): Project? {
    return openProjectAsync(virtualFile.toNioPath(), projectOpenOptions.toOpenProjectTask())
  }

  @Deprecated("Use openProjectAsync(VirtualFile, ProjectOpenOptions) instead",
              replaceWith = ReplaceWith("openProjectAsync(virtualFile, projectOpenOptions)"))
  override suspend fun openProjectAsync(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    val baseDir = virtualFile.toNioPath()
    val options = OpenProjectTask(createOptionsToOpenDotIdeaOrCreateNewIfNotExists(baseDir, projectToClose)) {
      this.forceOpenInNewFrame = forceOpenInNewFrame
    }
    return doOpenProject(baseDir, options)
  }

  // force open in a new frame if temp project
  override suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask): Project? {
    if (tempProject) {
      return createTempProjectAndOpenFile(file = file, options = OpenProjectTask(options) { forceOpenInNewFrame = true })
    }
    else {
      return openProjectAsync(file = file, originalOptions = options)
    }
  }

  override val name: String
    get() = "text editor"
}

/**
 * Opens a file named on the command line, once the project it belongs to is open.
 *
 * Every caller opens the project with [OpenProjectTask.opensFileAfterProjectOpen] set, so a hold on the editor empty state is waiting
 * to be released here: this navigation happens after project open has finished and released its own hold, and without the extra hold
 * the empty state would be shown for as long as it takes to get here and then immediately replaced by this file.
 */
@Internal
fun openFileFromCommandLine(project: Project, file: Path, line: Int, column: Int) {
  val wait = project.getUserData(COMMAND_LINE_FILE_WAIT)
  project.putUserData(COMMAND_LINE_FILE_WAIT, null)
  StartupManager.getInstance(project).runAfterOpened {
    project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(
      Dispatchers.Default + ModalityState.nonModal().asContextElement() + ClientId.coroutineContext(),
    ) {
      var navigated = false
      try {
        if (project.isDisposed || !Files.exists(file)) {
          return@launch
        }

        val virtualFile = ProjectUtilCore.getFileAndRefresh(file) ?: return@launch
        TrustedFiles.markExternallyOpened(virtualFile)
        navigated = navigateToFileFromCommandLine(project, virtualFile, line, column)
      }
      finally {
        withContext(Dispatchers.EDT) {
          if (!navigated && wait != null) {
            releaseCommandLineWaitIfNotOpen(wait)
          }
          endStartupEmptyStatePresentationHold(project)
        }
      }
    }
  }
}

/**
 * @return `false` if the navigation did nothing, eg, because a newer navigation superseded it
 */
internal suspend fun navigateToFileFromCommandLine(project: Project, file: VirtualFile, line: Int, column: Int): Boolean {
  val navigatable = withContext(Dispatchers.EDT) {
    if (line > 0) {
      OpenFileDescriptor(project, file, line - 1, column.coerceAtLeast(0))
    }
    else {
      PsiNavigationSupport.getInstance().createNavigatable(project, file, -1)
    }
  }
  val service = NavigationService.getInstance(project)
  val options = NavigationOptions.requestFocus().recordAsBackHistory(false)
  if (navigatable.javaClass == OpenFileDescriptor::class.java) {
    val descriptor = navigatable as OpenFileDescriptor
    try {
      return service.navigateRequests(options) {
        readAction {
          listOfNotNull(NavigationRequest.sourceNavigationRequest(project, descriptor.file, descriptor.offset))
        }
      }
    }
    finally {
      descriptor.dispose()
    }
  }
  else {
    return service.navigate(navigatable, options)
  }
}

/**
 * Ends [wait] with an error if its file is not open, because no editor can close it then.
 * An open file keeps the wait: a newer navigation can supersede the command line one after the editor tab appears.
 */
internal suspend fun releaseCommandLineWaitIfNotOpen(wait: CommandLineFileWait) {
  withContext(Dispatchers.EDT) {
    if (ProjectManagerEx.getOpenProjects().none { FileEditorManager.getInstance(it).isFileOpen(wait.file) }) {
      val result = CliResult(1, IdeBundle.message("dialog.message.can.not.open.file", wait.file.presentableUrl))
      CommandLineWaitingManager.getInstance().releaseHookForFile(wait.file, wait.future, result)
    }
  }
}

/**
 * Releases the hold [OpenProjectTask.opensFileAfterProjectOpen] asked for.
 *
 * Nothing to release if the editor area was never built: `mainSplitters` is a `lateinit` assigned inside `initJob`, and where that job
 * did not complete, editor restoring never took a hold either.
 */
@RequiresEdt
private fun endStartupEmptyStatePresentationHold(project: Project) {
  if (project.isDisposed) {
    return
  }
  val fileEditorManager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerImpl ?: return
  if (!fileEditorManager.initJob.isCompleted || fileEditorManager.initJob.isCancelled) {
    return
  }
  fileEditorManager.mainSplitters.endStartupEmptyStatePresentationHold()
}

@Internal
suspend fun attachToProjectAsync(
  projectToClose: Project,
  projectDir: Path,
  processor: ProjectAttachProcessor? = null,
  callback: ProjectOpenedCallback? = null,
  beforeOpen: (suspend (Project) -> Boolean)? = null,
): Boolean {
  if (!checkTrustedState(projectDir)) {
    return false
  }
  if (processor != null) {
    return attachSafe(processor, projectToClose, projectDir, callback, beforeOpen)
  }
  for (attachProcessor in ProjectAttachProcessor.EP_NAME.lazySequence()) {
    if (attachSafe(attachProcessor, projectToClose, projectDir, callback, beforeOpen)) {
      return true
    }
  }
  return false
}

@Internal
suspend fun attachSafe(
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
