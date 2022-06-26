// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.conversion.CannotConvertException
import com.intellij.conversion.ConversionResult
import com.intellij.conversion.ConversionService
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.opentelemetry.TraceManager
import com.intellij.diagnostic.runActivity
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
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.*
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
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
import com.intellij.project.ProjectStoreOwner
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.AppUIUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.util.ArrayUtil
import com.intellij.util.ModalityUiUtil
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.event.InvocationEvent
import java.io.IOException
import java.nio.file.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("OVERRIDE_DEPRECATION")
@Internal
open class ProjectManagerImpl : ProjectManagerEx(), Disposable {
  companion object {
    internal fun initProject(file: Path,
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
        @Suppress("DEPRECATION")
        ApplicationManager.getApplication().messageBus.syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(file, project)
        activity.end()
        registerComponents(project)
        project.stateStore.setPath(file, isRefreshVfsNeeded, template)
        project.init(preloadServices, indicator)
      }
      catch (initThrowable: Throwable) {
        try {
          WriteAction.runAndWait<RuntimeException> { Disposer.dispose(project) }
        }
        catch (disposeThrowable: Throwable) {
          initThrowable.addSuppressed(disposeThrowable)
        }
        throw initThrowable
      }
    }

    fun showCannotConvertMessage(e: CannotConvertException, component: Component?) {
      AppUIUtil.invokeOnEdt {
        Messages.showErrorDialog(component, IdeBundle.message("error.cannot.convert.project", e.message),
                                 IdeBundle.message("title.cannot.convert.project"))
      }
    }

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
    val project = ProjectExImpl(path, null)
    initProject(
      file = path,
      project = project,
      isRefreshVfsNeeded = true,
      preloadServices = true,
      template = null,
      indicator = ProgressManager.getInstance().progressIndicator
    )
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
  @ApiStatus.Internal
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

  override fun forceCloseProject(project: Project): Boolean {
    return closeProject(project = project,  saveProject = false, dispose = true, checkCanClose = false)
  }

  override fun saveAndForceCloseProject(project: Project): Boolean {
    return closeProject(project = project, saveProject = true, dispose = true, checkCanClose = false)
  }

  // return true if successful
  override fun closeAndDisposeAllProjects(checkCanClose: Boolean): Boolean {
    var projects = openProjects
    LightEditUtil.getProjectIfCreated()?.let {
      projects += it
    }
    for (project in projects) {
      if (!closeProject(project = project, saveProject = true, dispose = true, checkCanClose = checkCanClose)) {
        return false
      }
    }
    return true
  }

  protected open fun closeProject(project: Project, saveProject: Boolean, dispose: Boolean, checkCanClose: Boolean): Boolean {
    val app = ApplicationManager.getApplication()
    check(!app.isWriteAccessAllowed) {
      "Must not call closeProject() from under write action because fireProjectClosing() listeners must have a chance to do something useful"
    }

    app.assertIsWriteThread()
    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed;
      // if we are shutting down the entire test framework, proceed to full dispose
      val projectImpl = project as ProjectExImpl
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
          if (project is ProjectExImpl) {
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

    val result = AtomicBoolean()
    ShutDownTracker.getInstance().executeWithStopperThread(Thread.currentThread()) {
      if (project is ComponentManagerImpl) {
        (project as ComponentManagerImpl).stopServicePreloading()
      }
      publisher.projectClosingBeforeSave(project)
      if (saveProject) {
        FileDocumentManager.getInstance().saveAllDocuments()
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(project)
      }
      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return@executeWithStopperThread
      }

      // somebody can start progress here, do not wrap in write action
      fireProjectClosing(project)
      app.runWriteAction {
        removeFromOpened(project)
        if (project is ProjectExImpl) {
          // ignore dispose flag (dispose is passed only via deprecated API that used only by some 3d-party plugins)
          project.disposeEarlyDisposable()
          if (dispose) {
            project.startDispose()
          }
        }
        fireProjectClosed(project)
        ZipHandler.clearFileAccessorCache()
        LaterInvocator.purgeExpiredItems()
        if (dispose) {
          Disposer.dispose(project)
        }
      }
      result.set(true)
    }
    return result.get()
  }

  override fun closeAndDispose(project: Project): Boolean {
    return closeProject(project, saveProject = true, dispose = true, checkCanClose = true)
  }

  override fun addProjectManagerListener(listener: ProjectManagerListener) {
    listeners.add(listener)
  }

  override fun addProjectManagerListener(listener: VetoableProjectManagerListener) {
    listeners.add(listener)
  }

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

    var listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY)
    if (listeners == null) {
      listeners = (project as UserDataHolderEx)
        .putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY, ContainerUtil.createLockFreeCopyOnWriteList())
    }
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
        @Suppress("DEPRECATION")
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
     return newProject(toCanonicalName(path), OpenProjectTask(isNewProject = true, runConfigurators = false, projectName = name))
   }

   @Suppress("OVERRIDE_DEPRECATION")
   final override fun newProject(projectName: String?, filePath: String, useDefaultProjectSettings: Boolean, isDummy: Boolean): Project? {
     return newProject(toCanonicalName(filePath), OpenProjectTask(isNewProject = true,
                                                                  useDefaultProjectAsTemplate = useDefaultProjectSettings,
                                                                  projectName = projectName))
   }

   final override fun loadAndOpenProject(originalFilePath: String): Project? {
     return openProject(toCanonicalName(originalFilePath), OpenProjectTask())
   }

   final override fun openProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project? {
     return runBlocking { openProjectAsync(projectStoreBaseDir, options) }
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

         // this null assertion is required to overcome bug in new version of KT compiler: KT-40034
         @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
         if (checkExistingProjectOnOpen(projectToClose!!, options.callback, projectStoreBaseDir, options.projectName)) {
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
     val result = frameAllocator.run { indicator ->
       activity.end()
       val result = if (options.project == null) {
         prepareProject(options, projectStoreBaseDir) ?: return@run null
       }
       else {
         PrepareProjectResult(options.project as Project, null)
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

       result
     }.asDeferred().await()
     disableAutoSaveToken.finish()

     if (result == null) {
       frameAllocator.projectNotLoaded(error = null)
       if (options.showWelcomeScreen) {
         WelcomeFrame.showIfNoProjectOpened()
       }
       return null
     }

     val project = result.project
     options.callback?.projectOpened(project, result.module ?: ModuleManager.getInstance(project).modules[0])
     return project
   }

   private fun handleProjectOpenCancelOrFailure(project: Project) {
     val app = ApplicationManager.getApplication()
     app.invokeAndWait {
       closeProject(project, saveProject = false, dispose = true, checkCanClose = false)
     }
     app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
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
       app.invokeAndWait { closeProject(project, saveProject = false, dispose = true, checkCanClose = false) }
       app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed()
       if (!app.isUnitTestMode) {
         WelcomeFrame.showIfNoProjectOpened()
       }
       return false
     }
     return true
   }

   override fun newProject(file: Path, options: OpenProjectTask): Project? {
     removeProjectConfigurationAndCaches(file)

     val project = instantiateProject(file, options)
     try {
       val template = if (options.useDefaultProjectAsTemplate) defaultProject else null
       initProject(
         file = file,
         project = project,
         isRefreshVfsNeeded = options.isRefreshVfsNeeded,
         preloadServices = options.preloadServices,
         template = template,
         indicator = ProgressManager.getInstance().progressIndicator
       )
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

     if (options.beforeOpen != null && !options.beforeOpen!!(project)) {
       return null
     }

     if (options.runConfigurators && (options.isNewProject || ModuleManager.getInstance(project).modules.isEmpty()) ||
         project.isLoadedFromCacheButHasNoModules()) {
       val module = PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(projectStoreBaseDir, project,
                                                                                  options.isProjectCreatedWithWizard)
       options.preparedToOpen?.invoke(module)
       return PrepareProjectResult(project, module)
     }
     return PrepareProjectResult(project, module = null)
   }

   protected open fun isRunStartUpActivitiesEnabled(project: Project): Boolean = true

   private fun checkExistingProjectOnOpen(projectToClose: Project,
                                          callback: ProjectOpenedCallback?,
                                          projectDir: Path?,
                                          projectName: String?): Boolean {
     val settings = GeneralSettings.getInstance()
     val isValidProject = projectDir != null && ProjectUtilCore.isValidProjectPath(projectDir)
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

 private val tracer by lazy { TraceManager.getTracer("projectManager") }

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

   tracer.spanBuilder("open project")
     .setAttribute(AttributeKey.stringKey("project"), project.name)
     .useWithScope {
       val traceContext = Context.current()
       // invokeLater cannot be used for now
       executeInEdtWithProgress(indicator) {
         waitEdtActivity.end()

         indicator?.checkCanceled()

         if (indicator != null && ApplicationManager.getApplication().isInternal) {
           indicator.text = "Running project opened tasks..."  // NON-NLS (internal mode)
         }

         LOG.debug("projectOpened")

         val activity = StartUpMeasurer.startActivity("project opened callbacks")

         runActivity("projectOpened event executing") {
           tracer.spanBuilder("execute projectOpened handlers").setParent(traceContext).useWithScope {
             ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
           }
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
               LOG.error(e)
             }
           }

         activity.end()
       }

       ProjectImpl.ourClassesAreLoaded = true

       if (runStartUpActivities) {
         tracer.spanBuilder("StartupManager.projectOpened").useWithScope {
           (StartupManager.getInstance(project) as StartupManagerImpl).projectOpened(indicator)
         }
       }

       LifecycleUsageTriggerCollector.onProjectOpened(project)
       return CompletableFuture.completedFuture(null)
     }
 }

private val LOG = Logger.getInstance(ProjectManagerImpl::class.java)

private val LISTENERS_IN_PROJECT_KEY = Key.create<MutableList<ProjectManagerListener>>("LISTENERS_IN_PROJECT_KEY")

private val CLOSE_HANDLER_EP = ExtensionPointName<ProjectCloseHandler>("com.intellij.projectCloseHandler")

private fun getListeners(project: Project): List<ProjectManagerListener> {
  return project.getUserData(LISTENERS_IN_PROJECT_KEY) ?: return emptyList()
}

private val publisher: ProjectManagerListener
  get() = ApplicationManager.getApplication().messageBus.syncPublisher(ProjectManager.TOPIC)

private fun handleListenerError(e: Throwable, listener: ProjectManagerListener) {
  if (e is ProcessCanceledException) {
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
  (project as ComponentManagerEx).processInitializedComponents(com.intellij.openapi.components.ProjectComponent::class.java) { component, _ ->
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