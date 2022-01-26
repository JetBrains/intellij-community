// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.openapi.project.impl

import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.*
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.*
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.project.ProjectStoreOwner
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.ui.IdeUICustomization
import com.intellij.util.ModalityUiUtil
import com.intellij.util.ThreeState
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.event.InvocationEvent
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

@ApiStatus.Internal
open class ProjectManagerExImpl : ProjectManagerImpl() {
  final override fun createProject(name: String?, path: String): Project? {
    return newProject(toCanonicalName(path), OpenProjectTask(isNewProject = true, runConfigurators = false, projectName = name))
  }

  final override fun newProject(projectName: String?, path: String, useDefaultProjectAsTemplate: Boolean, isDummy: Boolean): Project? {
    return newProject(toCanonicalName(path), OpenProjectTask(isNewProject = true,
                                                             useDefaultProjectAsTemplate = useDefaultProjectAsTemplate,
                                                             projectName = projectName))
  }

  final override fun loadAndOpenProject(originalFilePath: String): Project? {
    return openProject(toCanonicalName(originalFilePath), OpenProjectTask())
  }

  final override fun openProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    return openProjectAsync(projectStoreBaseDir, options).get(30, TimeUnit.MINUTES)
  }

  final override fun openProjectAsync(projectStoreBaseDir: Path, options: OpenProjectTask): CompletableFuture<Project?> {
    if (LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode) {
      LOG.debug("open project: $options", Exception())
    }

    if (options.project != null && isProjectOpened(options.project as Project)) {
      return CompletableFuture.completedFuture(null)
    }

    if (!checkTrustedState(projectStoreBaseDir)) {
      return CompletableFuture.completedFuture(null)
    }

    val activity = StartUpMeasurer.startActivity("project opening preparation")
    if (!options.forceOpenInNewFrame) {
      val openProjects = openProjects
      if (!openProjects.isEmpty()) {
        var projectToClose = options.projectToClose
        if (projectToClose == null) {
          // if several projects are opened, ask to reuse not last opened project frame, but last focused (to avoid focus switching)
          val lastFocusedFrame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
          projectToClose = lastFocusedFrame?.project
          if (projectToClose == null || projectToClose is LightEditCompatible) {
            projectToClose = openProjects.last()
          }
        }

        // this null assertion is required to overcome bug in new version of KT compiler: KT-40034
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        if (checkExistingProjectOnOpen(projectToClose!!, options.callback, projectStoreBaseDir, options.projectName)) {
          return CompletableFuture.completedFuture(null)
        }
      }
    }

    return doOpenAsync(options, projectStoreBaseDir, activity)
  }

  private fun doOpenAsync(options: OpenProjectTask,
                          projectStoreBaseDir: Path,
                          activity: Activity): CompletableFuture<Project?> {
    val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) ProjectFrameAllocator(options)
    else ProjectUiFrameAllocator(options, projectStoreBaseDir)
    val disableAutoSaveToken = SaveAndSyncHandler.getInstance().disableAutoSave()
    return frameAllocator.run { indicator ->
      activity.end()
      val result: PrepareProjectResult
      if (options.project == null) {
        result = prepareProject(options, projectStoreBaseDir) ?: return@run null
      }
      else {
        result = PrepareProjectResult(options.project as Project, null)
      }

      val project = result.project
      if (!addToOpened(project)) {
        return@run null
      }

      if (!checkOldTrustedStateAndMigrate(project, projectStoreBaseDir)) {
        handleProjectOpenCancelOrFailure(project)
        return@run null
      }

      frameAllocator.projectLoaded(project)
      try {
        openProject(project, indicator, isRunStartUpActivitiesEnabled(project)).join()
      }
      catch (e: ProcessCanceledException) {
        handleProjectOpenCancelOrFailure(project)
        return@run null
      }

      frameAllocator.projectOpened(project)
      result
    }
      .handle(BiFunction { result, error ->
        disableAutoSaveToken.finish()

        if (error != null) {
          throw error
        }

        if (result == null) {
          frameAllocator.projectNotLoaded(error = null)
          if (options.showWelcomeScreen) {
            WelcomeFrame.showIfNoProjectOpened()
          }
          return@BiFunction null
        }

        val project = result.project
        if (options.callback != null) {
          options.callback!!.projectOpened(project, result.module ?: ModuleManager.getInstance(project).modules[0])
        }
        project
      })
  }

  private fun handleProjectOpenCancelOrFailure(project: Project) {
    ApplicationManager.getApplication().invokeAndWait {
      closeProject(project, /* saveProject = */false, /* dispose = */true, /* checkCanClose = */false)
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
  }

  /**
   * Checks if the project path is trusted, and shows the Trust Project dialog if needed.
   *
   * @return true if we should proceed with project opening, false if the process of project opening should be canceled.
   */
  private fun checkTrustedState(projectStoreBaseDir: Path): Boolean {
    val trustedPaths = TrustedPaths.getInstance()
    val trustedState = trustedPaths.getProjectPathTrustedState(projectStoreBaseDir)

    if (trustedState != ThreeState.UNSURE) {
      // the trusted state of this project path is already known => proceed with opening
      return true
    }

    if (isProjectImplicitlyTrusted(projectStoreBaseDir)) {
      return true
    }

    // check if the project trusted state could be known from the previous IDE version
    val metaInfo = (RecentProjectsManager.getInstance() as RecentProjectsManagerBase).getProjectMetaInfo(projectStoreBaseDir)
    val projectId = metaInfo?.projectWorkspaceId
    val productWorkspaceFile = PathManager.getConfigDir().resolve("workspace").resolve("$projectId.xml")
    if (projectId != null && productWorkspaceFile.exists()) {
      // this project is in recent projects => it was opened on this computer before
      // => most probably we already asked about its trusted state before
      // the only exception is: the project stayed in the UNKNOWN state in the previous version because it didn't utilize any dangerous features
      // in this case we will ask since no UNKNOWN state is allowed, but on a later stage, when we'll be able to look into the project-wide storage
      return true
    }

    return confirmOpeningAndSetProjectTrustedStateIfNeeded(projectStoreBaseDir)
  }

  /**
   * Checks if the project was trusted using the previous API.
   * Migrates the setting to the new API, shows the Trust Project dialog if needed.
   *
   * @return true if we should proceed with project opening, false if the process of project opening should be canceled.
   */
  private fun checkOldTrustedStateAndMigrate(project: Project, projectStoreBaseDir: Path): Boolean {
    val trustedPaths = TrustedPaths.getInstance()
    val trustedState = trustedPaths.getProjectPathTrustedState(projectStoreBaseDir)
    if (trustedState != ThreeState.UNSURE) {
      return true
    }

    @Suppress("DEPRECATION")
    val previousTrustedState = project.service<TrustedProjectSettings>().trustedState
    if (previousTrustedState != ThreeState.UNSURE) {
      // we were asking about this project in the previous IDE version => migrate
      trustedPaths.setProjectPathTrusted(projectStoreBaseDir, previousTrustedState.toBoolean())
      return true
    }

    return confirmOpeningAndSetProjectTrustedStateIfNeeded(projectStoreBaseDir)
  }

  override fun openProject(project: Project): Boolean {
    val store = if (project is ProjectStoreOwner) (project as ProjectStoreOwner).componentStore else null
    if (store != null) {
      val projectFilePath = if (store.storageScheme == StorageScheme.DIRECTORY_BASED) store.directoryStorePath!! else store.projectFilePath
      for (p in openProjects) {
        if (ProjectUtil.isSameProject(projectFilePath, p)) {
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL
          ) { ProjectUtil.focusProjectWindow(p, false) }
          return false
        }
      }
    }

    if (!addToOpened(project)) {
      return false
    }

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode && app.isDispatchThread) {
      LOG.warn("Do not open project in EDT")
    }

    try {
      openProject(project, ProgressManager.getInstance().progressIndicator, isRunStartUpActivitiesEnabled(project)).join()
    }
    catch (e: ProcessCanceledException) {
      app.invokeAndWait { closeProject(project, /* saveProject = */false, /* dispose = */true, /* checkCanClose = */false) }
      app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
      if (!app.isUnitTestMode) {
        WelcomeFrame.showIfNoProjectOpened()
      }
      return false
    }
    return true
  }

  override fun newProject(projectFile: Path, options: OpenProjectTask): Project? {
    removeProjectConfigurationAndCaches(projectFile)

    val project = instantiateProject(projectFile, options)
    try {
      val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
      initProject(projectFile, project, options.isRefreshVfsNeeded, options.preloadServices, template,
                  ProgressManager.getInstance().progressIndicator)
      project.setTrusted(true)
      return project
    }
    catch (t: Throwable) {
      handleErrorOnNewProject(t)
      return null
    }
  }

  protected open fun handleErrorOnNewProject(t: Throwable) {
    LOG.warn(t)
    try {
      val errorMessage = message(t)
      ApplicationManager.getApplication().invokeAndWait {
        Messages.showErrorDialog(errorMessage, ProjectBundle.message("project.load.default.error"))
      }
    }
    catch (e: NoClassDefFoundError) {
      // error icon not loaded
      LOG.info(e)
    }
  }

  protected open fun instantiateProject(projectStoreBaseDir: Path, options: OpenProjectTask): ProjectImpl {
    val activity = StartUpMeasurer.startActivity("project instantiation")
    val project = ProjectExImpl(projectStoreBaseDir, options.projectName)
    activity.end()
    options.beforeInit?.invoke(project)
    return project
  }

  private fun prepareProject(options: OpenProjectTask, projectStoreBaseDir: Path): PrepareProjectResult? {
    val project: Project?
    val indicator = ProgressManager.getInstance().progressIndicator
    if (options.isNewProject) {
      removeProjectConfigurationAndCaches(projectStoreBaseDir)
      project = instantiateProject(projectStoreBaseDir, options)
      val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
      initProject(projectStoreBaseDir, project, options.isRefreshVfsNeeded, options.preloadServices, template, indicator)
    }
    else {
      var conversionResult: ConversionResult? = null
      if (options.runConversionBeforeOpen) {
        val conversionService = ConversionService.getInstance()
        if (conversionService != null) {
          indicator?.text = IdeUICustomization.getInstance().projectMessage("progress.text.project.checking.configuration")
          conversionResult = runActivity("project conversion") {
            conversionService.convert(projectStoreBaseDir)
          }
          if (conversionResult.openingIsCanceled()) {
            return null
          }
          indicator?.text = ""
        }
      }

      project = instantiateProject(projectStoreBaseDir, options)
      // template as null here because it is not a new project
      initProject(projectStoreBaseDir, project, options.isRefreshVfsNeeded, options.preloadServices, null, indicator)
      if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
        StartupManager.getInstance(project).runAfterOpened {
          conversionResult.postStartupActivity(project)
        }
      }
    }

    project.putUserData(PlatformProjectOpenProcessor.PROJECT_NEWLY_OPENED, options.isNewProject)

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    if (options.beforeOpen != null && !options.beforeOpen!!(project)) {
      return null
    }

    if (options.runConfigurators && (options.isNewProject || ModuleManager.getInstance(project).modules.isEmpty())) {
      val module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(projectStoreBaseDir, project,
                                                                                 options.isProjectCreatedWithWizard)
      options.preparedToOpen?.invoke(module)
      return PrepareProjectResult(project, module)
    }
    else {
      return PrepareProjectResult(project, module = null)
    }
  }

  protected open fun isRunStartUpActivitiesEnabled(project: Project): Boolean = true

  private fun checkExistingProjectOnOpen(projectToClose: Project,
                                         callback: ProjectOpenedCallback?,
                                         projectDir: Path?,
                                         projectName: String?): Boolean {
    val settings = GeneralSettings.getInstance()
    val isValidProject = projectDir != null && ProjectUtil.isValidProjectPath(projectDir)
    var result = false

    // modality per thread, it means that we cannot use invokeLater, because after getting result from EDT, we MUST continue execution
    // in ORIGINAL thread
    ApplicationManager.getApplication().invokeAndWait task@{
      if (projectDir != null && ProjectAttachProcessor.canAttachToProject() &&
          (!isValidProject || settings.confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK)) {
        val exitCode = ProjectUtil.confirmOpenOrAttachProject()
        if (exitCode == -1) {
          result = true
          return@task
        }
        else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
          if (!closeAndDisposeKeepingFrame(projectToClose)) {
            result = true
            return@task
          }
        }
        else if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) {
          if (PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, callback)) {
            result = true
            return@task
          }
        }
        // process all pending events that can interrupt focus flow
        // todo this can be removed after taming the focus beast
        IdeEventQueue.getInstance().flushQueue()
      }
      else {
        val mode = GeneralSettings.getInstance().confirmOpenNewProject
        if (mode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH) {
          if (projectDir != null && PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, callback)) {
            result = true
            return@task
          }
        }

        val projectNameValue = projectName ?: projectDir?.fileName?.toString() ?: projectDir?.toString()
        val exitCode = ProjectUtil.confirmOpenNewProject(false, projectNameValue)
        if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
          if (!closeAndDisposeKeepingFrame(projectToClose)) {
            result = true
            return@task
          }
        }
        else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
          // not in a new window
          result = true
          return@task
        }
      }

      result = false
    }
    return result
  }

  private fun closeAndDisposeKeepingFrame(project: Project): Boolean {
    return (WindowManager.getInstance() as WindowManagerImpl).runWithFrameReuseEnabled { closeAndDispose(project) }
  }
}

@NlsSafe
private fun message(e: Throwable): String {
  var message = e.message ?: e.localizedMessage
  if (message != null) {
    return message
  }

  message = e.toString()
  val causeMessage = message(e.cause ?: return message)
  return "$message (cause: $causeMessage)"
}

private inline fun executeInEdtWithProgress(indicator: ProgressIndicator?, crossinline task: () -> Unit) {
  var pce: ProcessCanceledException? = null
  ApplicationManager.getApplication().invokeAndWait {
    try {
       if (indicator == null) {
        task()
      }
      else {
        ProgressManager.getInstance().executeProcessUnderProgress({ task() }, indicator)
      }
    }
    catch (e: ProcessCanceledException) {
      pce = e
    }
  }
  pce?.let { throw it }
}

private fun openProject(project: Project, indicator: ProgressIndicator?, runStartUpActivities: Boolean): CompletableFuture<*> {
  val waitEdtActivity = StartUpMeasurer.startActivity("placing calling projectOpened on event queue")
  if (indicator != null) {
    indicator.text = if (ApplicationManager.getApplication().isInternal) "Waiting on event queue..."  // NON-NLS (internal mode)
                     else ProjectBundle.message("project.preparing.workspace")
    indicator.isIndeterminate = true
  }

  // invokeLater cannot be used for now
  executeInEdtWithProgress(indicator) {
    waitEdtActivity.end()

    indicator?.checkCanceled()

    if (indicator != null && ApplicationManager.getApplication().isInternal) {
      indicator.text = "Running project opened tasks..."  // NON-NLS (internal mode)
    }

    ProjectManagerImpl.LOG.debug("projectOpened")

    val activity = StartUpMeasurer.startActivity("project opened callbacks")

    runActivity("projectOpened event executing") {
      ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
    }

    @Suppress("DEPRECATION")
    (project as ComponentManagerEx)
      .processInitializedComponents(com.intellij.openapi.components.ProjectComponent::class.java) { component, pluginDescriptor ->
        indicator?.checkCanceled()
        try {
          val componentActivity = StartUpMeasurer.startActivity(component.javaClass.name, ActivityCategory.PROJECT_OPEN_HANDLER,
                                                                pluginDescriptor.pluginId.idString)
          component.projectOpened()
          componentActivity.end()
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          ProjectManagerImpl.LOG.error(e)
        }
      }

    activity.end()
  }

  ProjectImpl.ourClassesAreLoaded = true

  if (runStartUpActivities) {
    (StartupManager.getInstance(project) as StartupManagerImpl).projectOpened(indicator)
  }

  LifecycleUsageTriggerCollector.onProjectOpened(project)
  return CompletableFuture.completedFuture(null)
}

// allow `invokeAndWait` inside startup activities
@TestOnly
internal fun waitAndProcessInvocationEventsInIdeEventQueue(startupManager: StartupManagerImpl) {
  ApplicationManager.getApplication().assertIsDispatchThread()
  val eventQueue = IdeEventQueue.getInstance()
  if (startupManager.postStartupActivityPassed()) {
    ApplicationManager.getApplication().invokeLater {}
  }
  else {
    // make sure eventQueue.nextEvent will unblock
    startupManager.registerPostStartupActivity(DumbAwareRunnable { ApplicationManager.getApplication().invokeLater{ } })
  }
  while (true) {
    val event = eventQueue.nextEvent
    if (event is InvocationEvent) {
      eventQueue.dispatchEvent(event)
    }
    if (startupManager.postStartupActivityPassed() && eventQueue.peekEvent() == null) {
      break
    }
  }
}

private data class PrepareProjectResult(val project: Project, val module: Module?)

private fun toCanonicalName(filePath: String): Path {
  val file = Paths.get(filePath)
  try {
    if (SystemInfoRt.isWindows && FileUtil.containsWindowsShortName(filePath)) {
      return file.toRealPath(LinkOption.NOFOLLOW_LINKS)
    }
  }
  catch (ignore: InvalidPathException) {
  }
  catch (e: IOException) {
    // OK. File does not yet exist, so its canonical path will be equal to its original path.
  }
  return file
}

private fun removeProjectConfigurationAndCaches(projectFile: Path) {
  try {
    if (Files.isRegularFile(projectFile)) {
      Files.deleteIfExists(projectFile)
    }
    else {
      Files.newDirectoryStream(projectFile.resolve(Project.DIRECTORY_STORE_FOLDER)).use { directoryStream ->
        for (file in directoryStream) {
          file!!.delete()
        }
      }
    }
  }
  catch (ignored: IOException) {
  }
  try {
    getProjectDataPathRoot(projectFile).delete()
  }
  catch (ignored: IOException) {
  }
}
