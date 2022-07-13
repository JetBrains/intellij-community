// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.*
import com.intellij.diagnostic.opentelemetry.TraceManager
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.*
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.*
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.IdeUICustomization
import com.intellij.util.ArrayUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

@Suppress("OVERRIDE_DEPRECATION")
@Internal
open class ProjectManagerImpl : ProjectManagerEx(), Disposable {
  companion object {
    @TestOnly
    @JvmStatic
    fun isLight(project: Project): Boolean {
      return project is ProjectEx && project.isLight
    }
  }

  private var openProjects = arrayOf<Project>() // guarded by lock
  private val openProjectByHash = ConcurrentHashMap<String, Project>()
  private val lock = Any()

  // we cannot use the same approach to migrate to message bus as CompilerManagerImpl because of method canCloseProject
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<ProjectManagerListener>()
  private val defaultProject = DefaultProject()
  private val excludeRootsCache: ExcludeRootsCache

  private var getAllExcludedUrlsCallback: Runnable? = null

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        for (listener in getAllListeners(project)) {
          try {
            @Suppress("DEPRECATION")
            listener.projectOpened(project)
          }
          catch (e: Exception) {
            handleListenerError(e, listener)
          }
        }
      }

      override fun projectClosed(project: Project) {
        for (listener in getAllListeners(project)) {
          try {
            listener.projectClosed(project)
          }
          catch (e: Exception) {
            handleListenerError(e, listener)
          }
        }
      }

      override fun projectClosing(project: Project) {
        for (listener in getAllListeners(project)) {
          try {
            listener.projectClosing(project)
          }
          catch (e: Exception) {
            handleListenerError(e, listener)
          }
        }
      }

      override fun projectClosingBeforeSave(project: Project) {
        for (listener in getAllListeners(project)) {
          try {
            listener.projectClosingBeforeSave(project)
          }
          catch (e: Exception) {
            handleListenerError(e, listener)
          }
        }
      }
    })
    excludeRootsCache = ExcludeRootsCache(connection)
  }

  @TestOnly
  fun testOnlyGetExcludedUrlsCallback(parentDisposable: Disposable, callback: Runnable) {
    check(getAllExcludedUrlsCallback == null) { "This method is not reentrant. Expected null but got $getAllExcludedUrlsCallback" }
    getAllExcludedUrlsCallback = callback
    Disposer.register(parentDisposable) { getAllExcludedUrlsCallback = null }
  }

  override val allExcludedUrls: List<String>
    get() {
      val callback = getAllExcludedUrlsCallback
      callback?.run()
      return excludeRootsCache.excludedUrls
    }

  override fun dispose() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    // dispose manually, because TimedReference.dispose() can already be called (in Timed.disposeTimed()) and then default project resurrected
    Disposer.dispose(defaultProject)
  }

  override fun loadProject(path: Path): Project {
    check(!ApplicationManager.getApplication().isDispatchThread)

    val project = ProjectImpl(filePath = path, projectName = null)
    val modalityState = CoreProgressManager.getCurrentThreadProgressModality()
    runBlocking(modalityState.asContextElement()) {
      initProject(
        file = path,
        project = project,
        isRefreshVfsNeeded = true,
        preloadServices = true,
        template = null,
        indicator = ProgressManager.getInstance().progressIndicator
      )
    }
    return project
  }

  override val isDefaultProjectInitialized: Boolean
    get() = defaultProject.isCached

  override fun getDefaultProject(): Project {
    LOG.assertTrue(!ApplicationManager.getApplication().isDisposed, "Application has already been disposed!")
    // call instance method to reset timeout
    // re-instantiate if needed
    val bus = defaultProject.messageBus
    LOG.assertTrue(!bus.isDisposed)
    LOG.assertTrue(defaultProject.isCached)
    return defaultProject
  }

  @TestOnly
  @Internal
  fun disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests() {
    defaultProject.disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests()
  }

  override fun getOpenProjects(): Array<Project> = synchronized(lock) { openProjects }

  override fun isProjectOpened(project: Project): Boolean = synchronized(lock) { openProjects.contains(project) }

  protected fun addToOpened(project: Project): Boolean {
    assert(!project.isDisposed) { "Must not open already disposed project" }
    synchronized(lock) {
      if (isProjectOpened(project)) {
        return false
      }
      openProjects += project
    }
    updateTheOnlyProjectField()
    openProjectByHash.put(project.locationHash, project)
    return true
  }

  fun updateTheOnlyProjectField() {
    val isLightEditActive = LightEditService.getInstance().project != null
    if (ApplicationManager.getApplication().isUnitTestMode && !ApplicationManagerEx.isInStressTest()) {
      // switch off optimization in non-stress tests to assert they don't query getProject for invalid PsiElements
      ProjectCoreUtil.updateInternalTheOnlyProjectFieldTemporarily(null)
    }
    else {
      val isDefaultInitialized = isDefaultProjectInitialized
      synchronized(lock) {
        val theOnlyProject = if (openProjects.size == 1 && !isDefaultInitialized && !isLightEditActive) openProjects.first() else null
        ProjectCoreUtil.updateInternalTheOnlyProjectFieldTemporarily(theOnlyProject)
      }
    }
  }

  private fun removeFromOpened(project: Project) {
    synchronized(lock) {
      openProjects = ArrayUtil.remove(openProjects, project)
      // remove by value and not by key!
      openProjectByHash.values.remove(project)
    }
  }

  override fun findOpenProjectByHash(locationHash: String?): Project? = openProjectByHash.get(locationHash)

  override fun reloadProject(project: Project) {
    StoreReloadManager.getInstance().reloadProject(project)
  }

  override fun closeProject(project: Project): Boolean {
    return closeProject(project = project, saveProject = true, dispose = false, checkCanClose = true)
  }

  override fun forceCloseProject(project: Project, save: Boolean): Boolean {
    return closeProject(project = project, saveProject = save, checkCanClose = false)
  }

  override suspend fun forceCloseProjectAsync(project: Project, save: Boolean): Boolean {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)
    if (save) {
      // HeadlessSaveAndSyncHandler doesn't save, but if `save` is requested,
      // it means that we must save in any case (for example, see GradleSourceSetsTest)
      saveSettings(project, forceSavingAllSettings = true)
    }
    return withContext(Dispatchers.EDT) {
      if (project.isDisposed) {
        return@withContext false
      }
      closeProject(project = project, saveProject = save, checkCanClose = false)
    }
  }

  // return true if successful
  override fun closeAndDisposeAllProjects(checkCanClose: Boolean): Boolean {
    var projects = openProjects
    LightEditUtil.getProjectIfCreated()?.let {
      projects += it
    }
    for (project in projects) {
      if (!closeProject(project = project, checkCanClose = checkCanClose)) {
        return false
      }
    }
    return true
  }

  protected open fun closeProject(project: Project, saveProject: Boolean = true, dispose: Boolean = true, checkCanClose: Boolean): Boolean {
    val app = ApplicationManager.getApplication()
    check(!app.isWriteAccessAllowed) {
      "Must not call closeProject() from under write action because fireProjectClosing() listeners must have a chance to do something useful"
    }

    app.assertIsWriteThread()
    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed;
      // if we are shutting down the entire test framework, proceed to full dispose
      val projectImpl = project as ProjectImpl
      if (!projectImpl.isTemporarilyDisposed) {
        ApplicationManager.getApplication().runWriteAction {
          projectImpl.disposeEarlyDisposable()
          projectImpl.setTemporarilyDisposed(true)
          removeFromOpened(project)
        }
        updateTheOnlyProjectField()
        return true
      }
      projectImpl.setTemporarilyDisposed(false)
    }
    else if (!isProjectOpened(project) && !LightEdit.owns(project)) {
      if (dispose) {
        if (project is ComponentManagerImpl) {
          project.stopServicePreloading()
        }
        ApplicationManager.getApplication().runWriteAction {
          if (project is ProjectImpl) {
            project.disposeEarlyDisposable()
            project.startDispose()
          }
          Disposer.dispose(project)
        }
      }
      return true
    }

    if (checkCanClose && !canClose(project)) {
      return false
    }

    if (project is ComponentManagerImpl) {
      (project as ComponentManagerImpl).stopServicePreloading()
    }
    publisher.projectClosingBeforeSave(project)
    if (saveProject) {
      FileDocumentManager.getInstance().saveAllDocuments()
      SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(project)
    }

    if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
      return false
    }

    // somebody can start progress here, do not wrap in write action
    fireProjectClosing(project)
    app.runWriteAction {
      removeFromOpened(project)
      if (project is ProjectImpl) {
        // ignore dispose flag (dispose is passed only via deprecated API that used only by some 3d-party plugins)
        project.disposeEarlyDisposable()
        if (dispose) {
          project.startDispose()
        }
      }
      fireProjectClosed(project)
      if (!ApplicationManagerEx.getApplicationEx().isExitInProgress) {
        ZipHandler.clearFileAccessorCache()
      }
      LaterInvocator.purgeExpiredItems()
      if (dispose) {
        Disposer.dispose(project)
      }
    }
    return true
  }

  override fun closeAndDispose(project: Project) = closeProject(project, checkCanClose = true)

  @Suppress("removal")
  override fun addProjectManagerListener(listener: ProjectManagerListener) {
    listeners.add(listener)
  }

  override fun addProjectManagerListener(listener: VetoableProjectManagerListener) {
    listeners.add(listener)
  }

  @Suppress("removal")
  override fun removeProjectManagerListener(listener: ProjectManagerListener) {
    val removed = listeners.remove(listener)
    LOG.assertTrue(removed)
  }

  override fun removeProjectManagerListener(listener: VetoableProjectManagerListener) {
    val removed = listeners.remove(listener)
    LOG.assertTrue(removed)
  }

  override fun addProjectManagerListener(project: Project, listener: ProjectManagerListener) {
    if (project.isDefault) {
      // nothing happens with default project
      return
    }

    val listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY)
                    ?: (project as UserDataHolderEx).putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY,
                                                                         ContainerUtil.createLockFreeCopyOnWriteList())
    listeners.add(listener)
  }

  override fun removeProjectManagerListener(project: Project, listener: ProjectManagerListener) {
    if (project.isDefault) {
      // nothing happens with default project
      return
    }

    val listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY)
    LOG.assertTrue(listeners != null)
    val removed = listeners!!.remove(listener)
    LOG.assertTrue(removed)
  }

  override fun canClose(project: Project): Boolean {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: canClose()")
    }

    for (handler in CLOSE_HANDLER_EP.iterable) {
      if (handler == null) {
        break
      }

      try {
        if (!handler.canClose(project)) {
          LOG.debug("close canceled by $handler")
          return false
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    for (listener in getAllListeners(project)) {
      try {
        @Suppress("DEPRECATION", "removal")
        val canClose = if (listener is VetoableProjectManagerListener) listener.canClose(project) else listener.canCloseProject(project)
        if (!canClose) {
          LOG.debug("close canceled by $listener")
          return false
        }
      }
      catch (e: Throwable) {
        handleListenerError(e, listener)
      }
    }
    return true
  }

  private fun getAllListeners(project: Project): List<ProjectManagerListener> {
    val projectLevelListeners = getListeners(project)
    // order is critically important due to backward compatibility - project level listeners must be first
    return when {
      projectLevelListeners.isEmpty() -> listeners
      listeners.isEmpty() -> projectLevelListeners
      else -> projectLevelListeners + listeners
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  final override fun createProject(name: String?, path: String): Project? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return newProject(toCanonicalName(path), OpenProjectTask {
      isNewProject = true
      runConfigurators = false
      projectName = name
    })
  }

  final override fun loadAndOpenProject(originalFilePath: String): Project? {
    return openProject(toCanonicalName(originalFilePath), OpenProjectTask())
  }

  final override fun openProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    return runUnderModalProgressIfIsEdt { openProjectAsync(projectStoreBaseDir, options) }
  }

  final override suspend fun openProjectAsync(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    if (LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode) {
      LOG.debug("open project: $options", Exception())
    }

    if (options.project != null && isProjectOpened(options.project as Project)) {
      return null
    }

    if (!checkTrustedState(projectStoreBaseDir)) {
      return null
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

        if (checkExistingProjectOnOpen(projectToClose, options, projectStoreBaseDir)) {
          return null
        }
      }
    }

    return doOpenAsync(options, projectStoreBaseDir, activity)
  }

  private suspend fun doOpenAsync(options: OpenProjectTask,
                                  projectStoreBaseDir: Path,
                                  activity: Activity): Project? {
    val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      ProjectFrameAllocator(options)
    }
    else {
      ProjectUiFrameAllocator(options, projectStoreBaseDir)
    }

    val disableAutoSaveToken = SaveAndSyncHandler.getInstance().disableAutoSave()
    var module: Module? = null
    val project: Project = try {
      frameAllocator.run {
        activity.end()
        val project: Project = options.project ?: prepareProject(options, projectStoreBaseDir)
        try {
          // must be under try-catch to dispose project on beforeOpen or preparedToOpen callback failures
          if (options.project == null) {
            val beforeOpen = options.beforeOpen
            if (beforeOpen != null && !beforeOpen(project)) {
              throw CancellationException("beforeOpen callback returned false")
            }

            if (options.runConfigurators &&
                (options.isNewProject || ModuleManager.getInstance(project).modules.isEmpty()) ||
                project.isLoadedFromCacheButHasNoModules()) {
              module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(
                baseDir = projectStoreBaseDir,
                project = project,
                newProject = options.isProjectCreatedWithWizard
              )
              options.preparedToOpen?.invoke(module!!)
            }
          }

          coroutineScope {
            launch {
              frameAllocator.projectLoaded(project)
            }

            if (!addToOpened(project)) {
              throw CancellationException("project is already opened")
            }

            if (!options.isNewProject && !checkOldTrustedStateAndMigrate(project, projectStoreBaseDir)) {
              throw CancellationException("not trusted")
            }

            val waitEdtActivity = StartUpMeasurer.startActivity("placing calling projectOpened on event queue")
            val indicator = ProgressManager.getInstance().progressIndicator
            if (indicator != null) {
              indicator.text = if (ApplicationManager.getApplication().isInternal) "Waiting on event queue..."  // NON-NLS (internal mode)
              else ProjectBundle.message("project.preparing.workspace")
              indicator.isIndeterminate = true
            }

            tracer.spanBuilder("open project")
              .setAttribute(AttributeKey.stringKey("project"), project.name)
              .useWithScope {
                runStartupActivities(project, indicator, isRunStartUpActivitiesEnabled(project), waitEdtActivity)
              }
          }

          project
        }
        catch (e: CancellationException) {
          withContext(NonCancellable) {
            try {
              withContext(Dispatchers.EDT) {
                closeProject(project, saveProject = false, checkCanClose = false)
              }
            }
            catch (secondException: Throwable) {
              e.addSuppressed(secondException)
            }
          }
          throw e
        }
      }
    }
    catch (e: CancellationException) {
      withContext(NonCancellable) {
        frameAllocator.projectNotLoaded(cannotConvertException = null)
      }
      throw e
    }
    catch (e: Throwable) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw e
      }

      LOG.error(e)
      frameAllocator.projectNotLoaded(cannotConvertException = e as? CannotConvertException)
      try {
        ApplicationManager.getApplication().messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
      }
      catch (secondException: Throwable) {
        LOG.error(secondException)
      }
      if (options.showWelcomeScreen) {
        WelcomeFrame.showIfNoProjectOpened()
      }
      return null
    }
    finally {
      disableAutoSaveToken.finish()
    }

    options.callback?.projectOpened(project, module ?: ModuleManager.getInstance(project).modules[0])
    return project
  }

  /**
   * Checks if the project path is trusted, and shows the Trust Project dialog if needed.
   *
   * @return true if we should proceed with project opening, false if the process of project opening should be canceled.
   */
  private suspend fun checkTrustedState(projectStoreBaseDir: Path): Boolean {
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

  override fun newProject(file: Path, options: OpenProjectTask): Project? {
    removeProjectConfigurationAndCaches(file)

    val project = instantiateProject(file, options)
    try {
      val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
      runUnderModalProgressIfIsEdt {
        initProject(
          file = file,
          project = project,
          isRefreshVfsNeeded = options.isRefreshVfsNeeded,
          preloadServices = options.preloadServices,
          template = template,
          indicator = ProgressManager.getInstance().progressIndicator
        )
      }
      project.setTrusted(true)
      return project
    }
    catch (t: Throwable) {
      handleErrorOnNewProject(t)
      return null
    }
  }

  override suspend fun newProjectAsync(file: Path, options: OpenProjectTask): Project {
    withContext(Dispatchers.IO) {
      removeProjectConfigurationAndCaches(file)
    }

    val project = instantiateProject(file, options)
    initProject(
      file = file,
      project = project,
      isRefreshVfsNeeded = options.isRefreshVfsNeeded,
      preloadServices = options.preloadServices,
      template = if (options.useDefaultProjectAsTemplate) defaultProject else null,
      indicator = ProgressManager.getInstance().progressIndicator
    )
    project.setTrusted(true)
    return project
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
    val project = ProjectImpl(filePath = projectStoreBaseDir, projectName = options.projectName)
    activity.end()
    options.beforeInit?.invoke(project)
    return project
  }

  private suspend fun prepareProject(options: OpenProjectTask, projectStoreBaseDir: Path): Project {
    val project: Project
    val indicator = ProgressManager.getInstance().progressIndicator
    if (options.isNewProject) {
      withContext(Dispatchers.IO) {
        removeProjectConfigurationAndCaches(projectStoreBaseDir)
      }
      project = instantiateProject(projectStoreBaseDir, options)
      val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
      initProject(file = projectStoreBaseDir,
                  project = project,
                  isRefreshVfsNeeded = options.isRefreshVfsNeeded,
                  preloadServices = options.preloadServices,
                  template = template,
                  indicator = indicator)

      project.putUserData(PlatformProjectOpenProcessor.PROJECT_NEWLY_OPENED, true)
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
            throw CancellationException("ConversionResult.openingIsCanceled() returned true")
          }
          indicator?.text = ""
        }
      }

      project = instantiateProject(projectStoreBaseDir, options)
      // template as null here because it is not a new project
      initProject(file = projectStoreBaseDir,
                  project = project,
                  isRefreshVfsNeeded = options.isRefreshVfsNeeded,
                  preloadServices = options.preloadServices,
                  template = null,
                  indicator = indicator)
      if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
        StartupManager.getInstance(project).runAfterOpened {
          conversionResult.postStartupActivity(project)
        }
      }
    }
    return project
  }

  protected open fun isRunStartUpActivitiesEnabled(project: Project): Boolean = true

  private suspend fun checkExistingProjectOnOpen(projectToClose: Project, options: OpenProjectTask, projectDir: Path?): Boolean {
    val isValidProject = projectDir != null && ProjectUtilCore.isValidProjectPath(projectDir)
    if (projectDir != null && ProjectAttachProcessor.canAttachToProject() &&
        (!isValidProject || GeneralSettings.getInstance().confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK)) {
      when (withContext(Dispatchers.EDT) { ProjectUtil.confirmOpenOrAttachProject() }) {
        -1 -> {
          return true
        }
        GeneralSettings.OPEN_PROJECT_SAME_WINDOW -> {
          if (!closeAndDisposeKeepingFrame(projectToClose)) {
            return true
          }
        }
        GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH -> {
          if (withContext(Dispatchers.EDT) { PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, options.callback) }) {
            return true
          }
        }
      }
    }
    else {
      val mode = GeneralSettings.getInstance().confirmOpenNewProject
      if (mode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH && projectDir != null &&
          withContext(Dispatchers.EDT) { PlatformProjectOpenProcessor.attachToProject(projectToClose, projectDir, options.callback) }) {
        return true
      }

      val projectNameValue = options.projectName ?: projectDir?.fileName?.toString() ?: projectDir?.toString()
      val exitCode = confirmOpenNewProject(options.copy(projectName = projectNameValue))
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        if (!closeAndDisposeKeepingFrame(projectToClose)) {
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

  private suspend fun closeAndDisposeKeepingFrame(project: Project): Boolean {
    return withContext(Dispatchers.EDT) {
      (WindowManager.getInstance() as WindowManagerImpl).withFrameReuseEnabled().use {
        closeProject(project, checkCanClose = true)
      }
    }
  }
}

private val tracer by lazy { TraceManager.getTracer("projectManager") }

@NlsSafe
private fun message(e: Throwable): String {
  var message = e.message ?: e.localizedMessage
  if (message != null) {
    return message
  }

  message = e.toString()
  return "$message (cause: ${message(e.cause ?: return message)})"
}

@Internal
@VisibleForTesting
suspend fun runStartupActivities(project: Project,
                                 indicator: ProgressIndicator?,
                                 runStartUpActivities: Boolean,
                                 waitEdtActivity: Activity?) {
  val traceContext = Context.current()
  withContext(Dispatchers.EDT) {
    waitEdtActivity?.end()

    if (indicator != null && ApplicationManager.getApplication().isInternal) {
      indicator.text = "Running project opened tasks..."  // NON-NLS (internal mode)
    }

    LOG.debug("projectOpened")

    val activity = StartUpMeasurer.startActivity("project opened callbacks")

    runActivity("projectOpened event executing") {
      tracer.spanBuilder("execute projectOpened handlers").setParent(traceContext).useWithScope {
        @Suppress("DEPRECATION")
        ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
      }
    }

    coroutineContext.ensureActive()

    @Suppress("DEPRECATION")
    (project as ComponentManagerImpl)
      .processInitializedComponents(com.intellij.openapi.components.ProjectComponent::class.java) { component, pluginDescriptor ->
        coroutineContext.ensureActive()
        try {
          val componentActivity = StartUpMeasurer.startActivity(component.javaClass.name, ActivityCategory.PROJECT_OPEN_HANDLER,
                                                                pluginDescriptor.pluginId.idString)
          component.projectOpened()
          componentActivity.end()
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }

    activity.end()
  }

  ProjectImpl.ourClassesAreLoaded = true

  if (runStartUpActivities) {
    val app = ApplicationManager.getApplication()
    if (indicator != null && app.isInternal) {
      indicator.text = IdeBundle.message("startup.indicator.text.running.startup.activities")
    }

    tracer.spanBuilder("StartupManager.projectOpened").useWithScope {
      (StartupManager.getInstance(project) as StartupManagerImpl).projectOpened(indicator)
    }
  }

  LifecycleUsageTriggerCollector.onProjectOpened(project)
}

private val LOG = logger<ProjectManagerImpl>()

private val LISTENERS_IN_PROJECT_KEY = Key.create<MutableList<ProjectManagerListener>>("LISTENERS_IN_PROJECT_KEY")

private val CLOSE_HANDLER_EP = ExtensionPointName<ProjectCloseHandler>("com.intellij.projectCloseHandler")

private fun getListeners(project: Project): List<ProjectManagerListener> {
  return project.getUserData(LISTENERS_IN_PROJECT_KEY) ?: return emptyList()
}

private val publisher: ProjectManagerListener
  get() = ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC)

private fun handleListenerError(e: Throwable, listener: ProjectManagerListener) {
  if (e is ProcessCanceledException || e is CancellationException) {
    throw e
  }
  else {
    LOG.error("From the listener $listener (${listener.javaClass})", e)
  }
}

private fun fireProjectClosing(project: Project) {
  if (LOG.isDebugEnabled) {
    LOG.debug("enter: fireProjectClosing()")
  }
  publisher.projectClosing(project)
}

private fun fireProjectClosed(project: Project) {
  if (LOG.isDebugEnabled) {
    LOG.debug("projectClosed")
  }

  LifecycleUsageTriggerCollector.onProjectClosed(project)
  publisher.projectClosed(project)
  @Suppress("DEPRECATION")
  val projectComponents = ArrayList<com.intellij.openapi.components.ProjectComponent>()
  @Suppress("DEPRECATION")
  (project as ComponentManagerImpl).processInitializedComponents(
    com.intellij.openapi.components.ProjectComponent::class.java) { component, _ ->
    projectComponents.add(component)
  }

  // see "why is called after message bus" in the fireProjectOpened
  for (i in projectComponents.indices.reversed()) {
    val component = projectComponents.get(i)
    try {
      component.projectClosed()
    }
    catch (e: Throwable) {
      LOG.error(component.toString(), e)
    }
  }
}

private fun ensureCouldCloseIfUnableToSave(project: Project): Boolean {
  val notificationManager = ApplicationManager.getApplication().getServiceIfCreated(NotificationsManager::class.java) ?: return true
  val notifications = notificationManager.getNotificationsOfType(UnableToSaveProjectNotification::class.java, project)
  if (notifications.isEmpty()) {
    return true
  }

  val message: @NlsContexts.DialogMessage StringBuilder = StringBuilder()
  message.append("${ApplicationNamesInfo.getInstance().productName} was unable to save some project files," +
                 "\nare you sure you want to close this project anyway?")
  message.append("\n\nRead-only files:\n")
  var count = 0
  val files = notifications.first().files
  for (file in files) {
    if (count == 10) {
      message.append('\n').append("and ").append(files.size - count).append(" more").append('\n')
    }
    else {
      message.append(file.path).append('\n')
      count++
    }
  }

  return Messages.showYesNoDialog(project, message.toString(),
                                  IdeUICustomization.getInstance().projectMessage("dialog.title.unsaved.project"),
                                  Messages.getWarningIcon()) == Messages.YES
}

class UnableToSaveProjectNotification(project: Project, readOnlyFiles: List<VirtualFile>) : Notification("Project Settings",
                                                                                                         IdeUICustomization.getInstance().projectMessage(
                                                                                                           "notification.title.cannot.save.project"),
                                                                                                         IdeBundle.message(
                                                                                                           "notification.content.unable.to.save.project.files"),
                                                                                                         NotificationType.ERROR) {
  private var project: Project?

  var files: List<VirtualFile>

  init {
    @Suppress("DEPRECATION")
    setListener { notification, _ ->
      val unableToSaveProjectNotification = notification as UnableToSaveProjectNotification
      val p = unableToSaveProjectNotification.project
      notification.expire()
      if (p != null && !p.isDisposed) {
        p.save()
      }
    }
    this.project = project
    files = readOnlyFiles
  }

  override fun expire() {
    project = null
    super.expire()
  }
}

private fun toCanonicalName(filePath: String): Path {
  val file = Path.of(filePath)
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

/**
 * Checks if the project was trusted using the previous API.
 * Migrates the setting to the new API, shows the Trust Project dialog if needed.
 *
 * @return true if we should proceed with project opening, false if the process of project opening should be canceled.
 */
private suspend fun checkOldTrustedStateAndMigrate(project: Project, projectStoreBaseDir: Path): Boolean {
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

private suspend fun initProject(file: Path,
                                project: ProjectImpl,
                                isRefreshVfsNeeded: Boolean,
                                preloadServices: Boolean,
                                template: Project?,
                                indicator: ProgressIndicator?) {
  LOG.assertTrue(!project.isDefault)

  try {
    if (indicator != null) {
      indicator.isIndeterminate = false
      // getting project name is not cheap and not possible at this moment
      indicator.text = ProjectBundle.message("project.loading.components")
    }
    val activity = StartUpMeasurer.startActivity("project before loaded callbacks")
    @Suppress("DEPRECATION", "removal")
    ApplicationManager.getApplication().messageBus.syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(file, project)
    activity.end()
    coroutineContext.ensureActive()
    registerComponents(project)
    coroutineContext.ensureActive()
    project.componentStore.setPath(file, isRefreshVfsNeeded, template)
    project.init(preloadServices, indicator)
  }
  catch (initThrowable: Throwable) {
    try {
      withContext(Dispatchers.EDT + NonCancellable) {
        ApplicationManager.getApplication().runWriteAction { Disposer.dispose(project) }
      }
    }
    catch (disposeThrowable: Throwable) {
      initThrowable.addSuppressed(disposeThrowable)
    }
    throw initThrowable
  }
}

@Suppress("DuplicatedCode")
private suspend fun confirmOpenNewProject(options: OpenProjectTask): Int {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return GeneralSettings.OPEN_PROJECT_NEW_WINDOW
  }

  var mode = GeneralSettings.getInstance().confirmOpenNewProject
  if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
    val message = if (options.projectName == null) {
      IdeBundle.message("prompt.open.project.in.new.frame")
    }
    else {
      IdeBundle.message("prompt.open.project.with.name.in.new.frame", options.projectName)
    }

    mode = withContext(Dispatchers.EDT) {
      if (options.isNewProject) {
        val openInExistingFrame = MessageDialogBuilder.yesNo(IdeCoreBundle.message("title.new.project"), message)
          .yesText(IdeBundle.message("button.existing.frame"))
          .noText(IdeBundle.message("button.new.frame"))
          .doNotAsk(ProjectNewWindowDoNotAskOption())
          .guessWindowAndAsk()
        if (openInExistingFrame) GeneralSettings.OPEN_PROJECT_SAME_WINDOW else GeneralSettings.OPEN_PROJECT_NEW_WINDOW
      }
      else {
        val exitCode = MessageDialogBuilder.yesNoCancel(IdeBundle.message("title.open.project"), message)
          .yesText(IdeBundle.message("button.existing.frame"))
          .noText(IdeBundle.message("button.new.frame"))
          .doNotAsk(ProjectNewWindowDoNotAskOption())
          .guessWindowAndAsk()
        when (exitCode) {
          Messages.YES -> GeneralSettings.OPEN_PROJECT_SAME_WINDOW
          Messages.NO -> GeneralSettings.OPEN_PROJECT_NEW_WINDOW
          else -> Messages.CANCEL
        }
      }
    }
    if (mode != Messages.CANCEL) {
      LifecycleUsageTriggerCollector.onProjectFrameSelected(mode)
    }
  }
  return mode
}

private suspend fun registerComponents(project: ProjectImpl) {
  var activity = createActivity(project) { "project ${StartUpMeasurer.Activities.REGISTER_COMPONENTS_SUFFIX}" }
  project.registerComponents()

  activity = activity?.endAndStart("projectComponentRegistered")
  val ep = ProjectServiceContainerCustomizer.getEp()
  for (adapter in ep.sortedAdapters) {
    coroutineContext.ensureActive()

    val pluginDescriptor = adapter.pluginDescriptor
    if (!isCorePlugin(pluginDescriptor)) {
      logger<ProjectImpl>().error(PluginException("Plugin $pluginDescriptor is not approved to add ${ep.name}", pluginDescriptor.pluginId))
      continue
    }

    (adapter.createInstance<ProjectServiceContainerCustomizer>(project) ?: continue).serviceRegistered(project)
  }
  activity?.end()
}

private inline fun createActivity(project: ProjectImpl, message: () -> String): Activity? {
  return if (!StartUpMeasurer.isEnabled() || project.isDefault) null else StartUpMeasurer.startActivity(message(), ActivityCategory.DEFAULT)
}

