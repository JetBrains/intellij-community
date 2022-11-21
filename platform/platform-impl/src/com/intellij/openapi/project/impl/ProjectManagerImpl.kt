// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.*
import com.intellij.diagnostic.telemetry.TraceManager
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.*
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.idea.canonicalPath
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.*
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.ex.ProjectManagerEx.Companion.IS_CHILD_PROCESS
import com.intellij.openapi.project.ex.ProjectManagerEx.Companion.PER_PROJECT_SUFFIX
import com.intellij.openapi.project.impl.ProjectImpl.Companion.preloadServicesAndCreateComponents
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
import com.intellij.util.Restarter
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.io.path.div

@Suppress("OVERRIDE_DEPRECATION")
@Internal
open class ProjectManagerImpl : ProjectManagerEx(), Disposable {
  companion object {
    @TestOnly
    @JvmStatic
    fun isLight(project: Project): Boolean {
      return project is ProjectEx && project.isLight
    }

    internal suspend fun dispatchEarlyNotifications() {
      val notificationManager = NotificationsManager.getNotificationsManager() as NotificationsManagerImpl
      withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
        notificationManager.dispatchEarlyNotifications()
      }
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
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(TOPIC, object : ProjectManagerListener {
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        for (listener in getAllListeners(project)) {
          try {
            @Suppress("DEPRECATION", "removal")
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

    // register unlocking perProject dirs action
    if (IS_PER_PROJECT_INSTANCE_READY) {
      connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosed(project: Project) {
          if (IS_CHILD_PROCESS) {
            clearPerProjectDirsForProject(PathManager.getSystemDir())
          }
          else {
            clearPerProjectDirsForProject(toPerProjectDir(PathManager.getSystemDir(), Path.of(project.basePath!!)))
          }
        }
      })
    }

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
        isTrustCheckNeeded = false,
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
    val isLightEditActive = serviceIfCreated<LightEditService>()?.project != null
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
    ApplicationManager.getApplication().assertIsNonDispatchThread()
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
    @Suppress("TestOnlyProblems")
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
    closePublisher.projectClosingBeforeSave(project)
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

    for (handler in CLOSE_HANDLER_EP.lazySequence()) {
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
    @Suppress("DEPRECATION")
    return runUnderModalProgressIfIsEdt { openProjectAsync(projectStoreBaseDir, options) }
  }

  final override suspend fun openProjectAsync(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
    if (LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode) {
      LOG.debug("open project: $options", Exception())
    }

    if (options.project != null && isProjectOpened(options.project as Project)) {
      LOG.info("Project is already opened -> return null")
      return null
    }

    val activity = StartUpMeasurer.startActivity("project opening preparation")

    if (!checkTrustedState(projectStoreBaseDir)) {
      LOG.info("Project is not trusted -> return null")
      return null
    }

    val shouldOpenInChildProcess = IS_PER_PROJECT_INSTANCE_ENABLED && openProjects.isNotEmpty() &&
                                   // Do not reopen previously opened projects in new instances
                                   !RecentProjectsManagerBase.getInstanceEx().isLastOpened(projectStoreBaseDir.toString())
    if (shouldOpenInChildProcess) {
      openInChildProcess(projectStoreBaseDir)
      return null
    }

    // if we are opening project in current process (not yet PER_PROJECT), lock per-project directory
    if (IS_PER_PROJECT_INSTANCE_READY) {
      if (IS_CHILD_PROCESS) {
        lockPerProjectDirForProject(PathManager.getSystemDir())
      }
      else {
        lockPerProjectDirForProject(toPerProjectDir(PathManager.getSystemDir(), projectStoreBaseDir))
      }
    }

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
          LOG.info("Project check is not succeeded -> return null")
          return null
        }
      }
    }

    return doOpenAsync(options, projectStoreBaseDir, activity)
  }

  private suspend fun doOpenAsync(options: OpenProjectTask, projectStoreBaseDir: Path, activity: Activity): Project? {
    val frameAllocator = if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      ProjectFrameAllocator(options)
    }
    else {
      ProjectUiFrameAllocator(options, projectStoreBaseDir)
    }

    val disableAutoSaveToken = SaveAndSyncHandler.getInstance().disableAutoSave()
    var module: Module? = null
    var result: Project? = null
    var projectOpenActivity: Activity? = null
    try {
      frameAllocator.run { saveTemplateJob, initFrame ->
        activity.end()
        val initFrameEarly = !options.isNewProject && options.beforeOpen == null
        val project = when {
          options.project != null -> options.project!!
          options.isNewProject -> prepareNewProject(options = options,
                                                    projectStoreBaseDir = projectStoreBaseDir,
                                                    saveTemplateJob = saveTemplateJob)
          else -> prepareProject(options = options,
                                 projectStoreBaseDir = projectStoreBaseDir,
                                 initFrame = initFrame.takeIf { initFrameEarly })
        }
        result = project
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

        if (!addToOpened(project)) {
          throw CancellationException("project is already opened")
        }

        // Project is loaded and is initialized, project services and components can be accessed.
        // But start-up and post start-up activities are not yet executed.
        if (!initFrameEarly) {
          initFrame(project)
        }

        projectOpenActivity = if (StartUpMeasurer.isEnabled()) StartUpMeasurer.startActivity("project opening") else null
        runActivity("project startup") {
          tracer.spanBuilder("open project")
            .setAttribute(AttributeKey.stringKey("project"), project.name)
          runInitProjectActivities(project)
        }
      }
    }
    catch (e: CancellationException) {
      withContext(NonCancellable) {
        result?.let { project ->
          try {
            try {
              @Suppress("DEPRECATION")
              // cancel async preloading of services as soon as possible
              project.coroutineScope.coroutineContext.job.cancelAndJoin()
            }
            catch (secondException: Throwable) {
              e.addSuppressed(secondException)
            }

            withContext(Dispatchers.EDT) {
              closeProject(project, saveProject = false, checkCanClose = false)
            }
          }
          catch (secondException: Throwable) {
            e.addSuppressed(secondException)
          }
        }
        failedToOpenProject(frameAllocator = frameAllocator, exception = null, options = options)
      }
      throw e
    }
    catch (e: Throwable) {
      result?.let { project ->
        try {
          withContext(Dispatchers.EDT) {
            closeProject(project, saveProject = false, checkCanClose = false)
          }
        }
        catch (secondException: Throwable) {
          e.addSuppressed(secondException)
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw e
      }

      LOG.error(e)
      failedToOpenProject(frameAllocator = frameAllocator, exception = e, options = options)
      return null
    }
    finally {
      disableAutoSaveToken.finish()

      projectOpenActivity?.end()
    }

    val project = result!!
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val openTimestamp = System.currentTimeMillis()
      @Suppress("DEPRECATION")
      project.coroutineScope?.launch {
        notifyRecentManager(project, options, openTimestamp)
      }
    }

    if (isRunStartUpActivitiesEnabled(project)) {
      (StartupManager.getInstance(project) as StartupManagerImpl).runPostStartupActivities()
    }
    LifecycleUsageTriggerCollector.onProjectOpened(project)

    options.callback?.projectOpened(project, module ?: ModuleManager.getInstance(project).modules[0])
    return project
  }

  private suspend fun notifyRecentManager(project: Project, options: OpenProjectTask, openTimestamp: Long) {
    RecentProjectsManagerBase.getInstanceEx().projectOpened(
      project = project,
      recentProjectMetaInfo = ((options.implOptions as? OpenProjectImplOptions))?.recentProjectMetaInfo,
      openTimestamp = openTimestamp,
    )
    dispatchEarlyNotifications()
  }

  private suspend fun failedToOpenProject(frameAllocator: ProjectFrameAllocator, exception: Throwable?, options: OpenProjectTask) {
    frameAllocator.projectNotLoaded(cannotConvertException = exception as? CannotConvertException)
    try {
      ApplicationManager.getApplication().messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
    }
    catch (secondException: Throwable) {
      LOG.error(secondException)
    }
    if (options.showWelcomeScreen) {
      WelcomeFrame.showIfNoProjectOpened()
    }
  }

  override fun newProject(file: Path, options: OpenProjectTask): Project? {
    removeProjectConfigurationAndCaches(file)

    val project = instantiateProject(file, options)
    try {
      val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
      @Suppress("DEPRECATION")
      runUnderModalProgressIfIsEdt {
        initProject(
          file = file,
          project = project,
          isRefreshVfsNeeded = options.isRefreshVfsNeeded,
          preloadServices = options.preloadServices,
          template = template,
          isTrustCheckNeeded = false,
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
      isTrustCheckNeeded = false,
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

  private suspend fun prepareNewProject(options: OpenProjectTask, projectStoreBaseDir: Path, saveTemplateJob: Job?): Project {
    withContext(Dispatchers.IO) {
      removeProjectConfigurationAndCaches(projectStoreBaseDir)
    }

    val project = instantiateProject(projectStoreBaseDir, options)
    saveTemplateJob?.join()
    val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
    initProject(file = projectStoreBaseDir,
                project = project,
                isRefreshVfsNeeded = options.isRefreshVfsNeeded,
                preloadServices = options.preloadServices,
                template = template,
                isTrustCheckNeeded = false)

    project.putUserData(PlatformProjectOpenProcessor.PROJECT_NEWLY_OPENED, true)
    return project
  }

  private suspend fun prepareProject(options: OpenProjectTask,
                                     projectStoreBaseDir: Path,
                                     initFrame: ((project: Project) -> Unit)?): Project {
    var conversionResult: ConversionResult? = null
    if (options.runConversionBeforeOpen) {
      val conversionService = ConversionService.getInstance()
      if (conversionService != null) {
        conversionResult = runActivity("project conversion") {
          conversionService.convert(projectStoreBaseDir)
        }
        if (conversionResult.openingIsCanceled()) {
          throw CancellationException("ConversionResult.openingIsCanceled() returned true")
        }
      }
    }

    val project = instantiateProject(projectStoreBaseDir, options)

    // template as null here because it is not a new project
    initProject(file = projectStoreBaseDir,
                project = project,
                isRefreshVfsNeeded = options.isRefreshVfsNeeded,
                preloadServices = options.preloadServices,
                template = null,
                initFrame = initFrame,
                isTrustCheckNeeded = true)

    if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
      StartupManager.getInstance(project).runAfterOpened {
        conversionResult.postStartupActivity(project)
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
fun CoroutineScope.runInitProjectActivities(project: Project) {
  launch {
    (StartupManager.getInstance(project) as StartupManagerImpl).initProject()
  }

  val waitEdtActivity = StartUpMeasurer.startActivity("placing calling projectOpened on event queue")
  launchAndMeasure("projectOpened event executing", Dispatchers.EDT) {
    waitEdtActivity.end()
    tracer.spanBuilder("projectOpened event executing").useWithScope2 {
      @Suppress("DEPRECATION", "removal")
      ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
    }
  }

  @Suppress("DEPRECATION")
  val projectComponents = (project as ComponentManagerImpl)
    .collectInitializedComponents(com.intellij.openapi.components.ProjectComponent::class.java)
  if (projectComponents.isEmpty()) {
    return
  }

  launchAndMeasure("projectOpened component executing", Dispatchers.EDT) {
    for (component in projectComponents) {
      try {
        val componentActivity = StartUpMeasurer.startActivity(component.javaClass.name, ActivityCategory.PROJECT_OPEN_HANDLER)
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
  }
}

private val LOG = logger<ProjectManagerImpl>()

private val LISTENERS_IN_PROJECT_KEY = Key.create<MutableList<ProjectManagerListener>>("LISTENERS_IN_PROJECT_KEY")

private val CLOSE_HANDLER_EP = ExtensionPointName<ProjectCloseHandler>("com.intellij.projectCloseHandler")

private fun getListeners(project: Project): List<ProjectManagerListener> {
  return project.getUserData(LISTENERS_IN_PROJECT_KEY) ?: return emptyList()
}

private val publisher: ProjectManagerListener
  get() = ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC)
private val closePublisher: ProjectCloseListener
  get() = ApplicationManager.getApplication().messageBus.syncPublisher(ProjectCloseListener.TOPIC)

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
  try {
    closePublisher.projectClosing(project)
    publisher.projectClosing(project)
  }
  catch (e: Throwable) {
    LOG.warn("Failed to publish projectClosing(project) event", e)
  }
}

private fun fireProjectClosed(project: Project) {
  if (LOG.isDebugEnabled) {
    LOG.debug("projectClosed")
  }

  LifecycleUsageTriggerCollector.onProjectClosed(project)
  closePublisher.projectClosed(project)
  publisher.projectClosed(project)
  @Suppress("DEPRECATION")
  val projectComponents = (project as ComponentManagerImpl)
    .collectInitializedComponents(com.intellij.openapi.components.ProjectComponent::class.java)

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

  @Suppress("HardCodedStringLiteral")
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
                                isTrustCheckNeeded: Boolean,
                                initFrame: ((project: Project) -> Unit)? = null) {
  LOG.assertTrue(!project.isDefault)

  try {
    coroutineContext.ensureActive()

    val registerComponentsActivity = createActivity(project) { "project ${StartUpMeasurer.Activities.REGISTER_COMPONENTS_SUFFIX}" }
    project.registerComponents()
    registerComponentsActivity?.end()

    if (ApplicationManager.getApplication().isUnitTestMode) {
      @Suppress("TestOnlyProblems")
      for (listener in ProjectServiceContainerCustomizer.getEp().extensionList) {
        listener.serviceRegistered(project)
      }
    }

    coroutineContext.ensureActive()
    project.componentStore.setPath(file, isRefreshVfsNeeded, template)

    coroutineScope {
      val isTrusted = async { !isTrustCheckNeeded || checkOldTrustedStateAndMigrate(project, file) }

      projectInitListeners {
        it.execute(project)
      }

      // yes, before preloadServicesAndCreateComponents
      initFrame?.invoke(project)

      preloadServicesAndCreateComponents(project, preloadServices)

      if (!isTrusted.await()) {
        throw CancellationException("not trusted")
      }
    }
  }
  catch (initThrowable: Throwable) {
    try {
      withContext(NonCancellable) {
        project.coroutineScope.coroutineContext.job.cancelAndJoin()
        writeAction {
          Disposer.dispose(project)
        }
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

    val openInExistingFrame = withContext(Dispatchers.EDT) {
      if (options.isNewProject)
        MessageDialogBuilder.yesNoCancel(IdeUICustomization.getInstance().projectMessage("title.new.project"), message)
          .yesText(IdeBundle.message("button.existing.frame"))
          .noText(IdeBundle.message("button.new.frame"))
          .doNotAsk(ProjectNewWindowDoNotAskOption())
          .guessWindowAndAsk()
      else
        MessageDialogBuilder.yesNoCancel(IdeUICustomization.getInstance().projectMessage("title.open.project"), message)
          .yesText(IdeBundle.message("button.existing.frame"))
          .noText(IdeBundle.message("button.new.frame"))
          .doNotAsk(ProjectNewWindowDoNotAskOption())
          .guessWindowAndAsk()
    }

    mode = when (openInExistingFrame) {
      Messages.YES -> GeneralSettings.OPEN_PROJECT_SAME_WINDOW
      Messages.NO -> GeneralSettings.OPEN_PROJECT_NEW_WINDOW
      else -> Messages.CANCEL
    }
    if (mode != Messages.CANCEL) {
      LifecycleUsageTriggerCollector.onProjectFrameSelected(mode)
    }
  }
  return mode
}

private inline fun createActivity(project: ProjectImpl, message: () -> String): Activity? {
  return if (!StartUpMeasurer.isEnabled() || project.isDefault) null else StartUpMeasurer.startActivity(message(), ActivityCategory.DEFAULT)
}

internal suspend inline fun projectInitListeners(crossinline executor: suspend (ProjectServiceContainerInitializedListener) -> Unit) {
  val extensionArea = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
  val ep = extensionArea
    .getExtensionPoint<ProjectServiceContainerInitializedListener>("com.intellij.projectServiceContainerInitializedListener")
  for (adapter in ep.sortedAdapters) {
    val pluginDescriptor = adapter.pluginDescriptor
    if (!isCorePlugin(pluginDescriptor)) {
      LOG.error(PluginException("Plugin $pluginDescriptor is not approved to add ${ep.name}", pluginDescriptor.pluginId))
      continue
    }

    try {
      executor(adapter.createInstance(ep.componentManager) ?: continue)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}

internal fun isCorePlugin(descriptor: PluginDescriptor): Boolean {
  val id = descriptor.pluginId
  return id == PluginManagerCore.CORE_ID ||
         // K/N Platform Deps is a repackaged Java plugin
         id.idString == "com.intellij.kotlinNative.platformDeps"
}

/**
 * Usage requires IJ Platform team approval (including plugin into white-list).
 */
@Internal
interface ProjectServiceContainerInitializedListener {
  /**
   * Invoked after container configured.
   */
  suspend fun execute(project: Project)
}

@TestOnly
interface ProjectServiceContainerCustomizer {
  companion object {
    @TestOnly
    fun getEp(): ExtensionPointImpl<ProjectServiceContainerCustomizer> {
      return (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint("com.intellij.projectServiceContainerCustomizer")
    }
  }

  /**
   * Invoked after implementation classes for project's components were determined (and loaded),
   * but before components are instantiated.
   */
  fun serviceRegistered(project: Project)
}

private fun readOneLine(file: Path) = Files.newBufferedReader(file).use { it.readLine().trim() }

private fun copyLineFromFileToNewSystemDir(fileName: String, systemDir: Path) {
  val line = readOneLine(PathManager.getSystemDir().resolve(fileName))
  val newPath = systemDir.resolve(fileName)
  File(newPath.parent.toUri()).mkdirs()
  Files.write(newPath, line.toByteArray(StandardCharsets.UTF_8))
}

// TODO actual FileLocks?
private fun lockPerProjectDirForProject(
  systemDir: Path,
) {
  // copy current token
  copyLineFromFileToNewSystemDir(SpecialConfigFiles.TOKEN_FILE, systemDir)

  // copy current port
  copyLineFromFileToNewSystemDir(SpecialConfigFiles.PORT_FILE, systemDir)

  PathManager.lockPerProjectPath(systemDir)
}

private fun deleteFileFromNewSystemDir(fileName: String, systemDir: Path) {
  val filePath = systemDir.resolve(fileName)
  if (filePath.exists()) Files.delete(filePath)
}

// TODO actual FileLocks?
private fun clearPerProjectDirsForProject(
  systemDir: Path,
) {
  PathManager.unlockPerProjectPath(systemDir)

  // delete current token
  deleteFileFromNewSystemDir(SpecialConfigFiles.TOKEN_FILE, systemDir)

  // delete current port
  deleteFileFromNewSystemDir(SpecialConfigFiles.PORT_FILE, systemDir)
}

/**
 * Checks if the project path is trusted, and shows the Trust Project dialog if needed.
 *
 * @return true if we should proceed with project opening, false if the process of project opening should be canceled.
 */
private suspend fun checkTrustedState(projectStoreBaseDir: Path): Boolean {
  val trustedState = TrustedPaths.getInstance().getProjectPathTrustedState(projectStoreBaseDir)
  if (trustedState != ThreeState.UNSURE) {
    // the trusted state of this project path is already known => proceed with opening
    return true
  }

  if (isProjectImplicitlyTrusted(projectStoreBaseDir)) {
    return true
  }

  // check if the project trusted state could be known from the previous IDE version
  val metaInfo = RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(projectStoreBaseDir)
  val projectId = metaInfo?.projectWorkspaceId
  val productWorkspaceFile = PathManager.getConfigDir().resolve("workspace").resolve("$projectId.xml")
  if (projectId != null && Files.exists(productWorkspaceFile)) {
    // this project is in recent projects => it was opened on this computer before
    // => most probably we already asked about its trusted state before
    // the only exception is: the project stayed in the UNKNOWN state in the previous version because it didn't utilize any dangerous features
    // in this case we will ask since no UNKNOWN state is allowed, but on a later stage, when we'll be able to look into the project-wide storage
    return true
  }

  return confirmOpeningAndSetProjectTrustedStateIfNeeded(projectStoreBaseDir)
}

private suspend fun openInChildProcess(projectStoreBaseDir: Path) {
  try {
    withContext(Dispatchers.IO) {
      ProcessBuilder(openProjectInstanceCommand(projectStoreBaseDir))
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(PathManager.getLogDir().resolve("idea.log").toFile()))
        .start()
        .also {
          LOG.info("Child process started, PID: ${it.pid()}")
        }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    LOG.error(e)
  }
}

private fun toPerProjectDir(path: Path, projectStoreBaseDir: Path): Path {
  val projectStoreBaseDirRelative = Paths.get("/").relativize(projectStoreBaseDir)
  return path / PER_PROJECT_SUFFIX / projectStoreBaseDirRelative
}

private fun removePerProjectSuffix(path: Path, currentProjectBaseDir: Path): Path {
  val projectStoreBaseDirRelative = Paths.get("/").relativize(currentProjectBaseDir)
  val suffix = PER_PROJECT_SUFFIX + File.separator + projectStoreBaseDirRelative
  return canonicalPath(path.toString().removeSuffix(suffix))
}

private fun openProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
  return listOf(
  "open",
  "-n",
  Restarter.getIdeStarter().toString(),
  "--args",

  *(mapOf(
      PathManager.PROPERTY_SYSTEM_PATH to PathManager.getSystemDir(),
      PathManager.PROPERTY_CONFIG_PATH to PathManager.getConfigDir(),
      PathManager.PROPERTY_LOG_PATH to PathManager.getLogDir(),
      PathManager.PROPERTY_PLUGINS_PATH to PathManager.getPluginsDir(),
    ).mapValuesTo(mutableMapOf()) { (key, value) ->
      val currentProjectBaseDir = Paths.get(ProjectManagerEx.getOpenProjects().first().basePath ?: "")
      val baseDir = if (IS_CHILD_PROCESS) removePerProjectSuffix(value, currentProjectBaseDir) else value

      "-D$key=${toPerProjectDir(baseDir, projectStoreBaseDir)}"
    }.values.toTypedArray()
   ),

  //for (vmOption in VMOptions.readOptions("", true)) {
  //  command += vmOption.asPatchedAgentLibOption()
  //             ?: vmOption.asPatchedVMOption("splash", "false")
  //             ?: vmOption.asPatchedVMOption("nosplash", "true")
  //             ?: vmOption.asPatchedVMOption(ConfigImportHelper.SHOW_IMPORT_CONFIG_DIALOG_PROPERTY, "default-production")
  //             ?: customProperties.keys.firstOrNull { vmOption.isVMOption(it) }?.let { customProperties.remove(it) }
  //             ?: vmOption
  //}

  projectStoreBaseDir.toString(),
  )
}