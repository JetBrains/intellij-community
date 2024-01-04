// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.idea

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.internal.inspector.UiInspectorAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.bootstrap.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.alsoIfNull
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR
import kotlinx.coroutines.*
import java.nio.file.Path
import java.util.*
import javax.swing.JOptionPane

open class IdeStarter : ModernApplicationStarter() {
  companion object {
    private var filesToLoad: List<Path> = Collections.emptyList()
    private var uriToOpen: String? = null

    @JvmStatic
    fun openFilesOnLoading(value: List<Path>) {
      filesToLoad = value
    }

    @JvmStatic
    fun openUriOnLoading(value: String) {
      uriToOpen = value
    }
  }

  override val isHeadless: Boolean
    get() = false

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
  override val commandName: String?
    get() = null

  override suspend fun start(args: List<String>) {
    coroutineScope {
      val app = ApplicationManager.getApplication()
      val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)

      val openProjectBlock: suspend CoroutineScope.() -> Unit = {
        openProjectIfNeeded(args = args, app = app, asyncCoroutineScope = this, lifecyclePublisher = lifecyclePublisher)
      }

      FUSProjectHotStartUpMeasurer.getStartUpContextElementIntoIdeStarter(this@IdeStarter)?.also {
        if ((app as ApplicationEx).isLightEditMode) {
          FUSProjectHotStartUpMeasurer.lightEditProjectFound()
        }
        withContext(it, openProjectBlock)
      }.alsoIfNull { openProjectBlock() }

      app.serviceAsync<PerformanceWatcher>()
      // cache it as IdeEventQueue should use loaded PerformanceWatcher service as soon as it is ready (getInstanceIfCreated is used)
      PerformanceWatcher.getInstance().startEdtSampling()

      launch { reportPluginErrors() }

      LoadingState.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)
      runCatching {
        lifecyclePublisher.appStarted()
      }.getOrLogException(thisLogger())

      if (!app.isHeadlessEnvironment) {
        postOpenUiTasks()
      }
    }
  }

  @OptIn(IntellijInternalApi::class)
  protected open suspend fun openProjectIfNeeded(args: List<String>,
                                                 app: Application,
                                                 asyncCoroutineScope: CoroutineScope,
                                                 lifecyclePublisher: AppLifecycleListener) {
    var willReopenRecentProjectOnStart = false
    lateinit var recentProjectManager: RecentProjectsManager
    val isOpenProjectNeeded = span("isOpenProjectNeeded") {
      span("app frame created callback") {
        runCatching {
          lifecyclePublisher.appFrameCreated(args)
        }.getOrLogException(thisLogger())
      }

      // must be after `AppLifecycleListener#appFrameCreated`, because some listeners can mutate the state of `RecentProjectsManager`
      if (app.isHeadlessEnvironment) {
        LifecycleUsageTriggerCollector.onIdeStart()
        return@span false
      }

      asyncCoroutineScope.launch {
        LifecycleUsageTriggerCollector.onIdeStart()
      }

      if (app.isInternal) {
        asyncCoroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          UiInspectorAction.initStacktracesSaving()
        }
      }

      if (uriToOpen != null || args.isNotEmpty() && args.first().contains(SCHEME_SEPARATOR)) {
        FUSProjectHotStartUpMeasurer.reportUriOpening()
        processUriParameter(uri = uriToOpen ?: args.first(), lifecyclePublisher = lifecyclePublisher)
        return@span false
      }

      recentProjectManager = serviceAsync<RecentProjectsManager>()
      willReopenRecentProjectOnStart = recentProjectManager.willReopenProjectOnStart()
      val willOpenProject = willReopenRecentProjectOnStart || !args.isEmpty() || !filesToLoad.isEmpty()
      willOpenProject || showWelcomeFrame(lifecyclePublisher)
    }

    if (!isOpenProjectNeeded) {
      return
    }

    val project = when {
      filesToLoad.isNotEmpty() -> {
        FUSProjectHotStartUpMeasurer.reportProjectType(FUSProjectHotStartUpMeasurer.ProjectsType.FromFilesToLoad)
        ProjectUtil.openOrImportFilesAsync(filesToLoad, "IdeStarter")
      }
      args.isNotEmpty() -> {
        FUSProjectHotStartUpMeasurer.reportProjectType(FUSProjectHotStartUpMeasurer.ProjectsType.FromArgs)
        loadProjectFromExternalCommandLine(args)
      }
      else -> null
    }

    if (project != null) {
      return
    }

    val isOpened = willReopenRecentProjectOnStart && try {
      span("reopenLastProjectsOnStart") {
        recentProjectManager.reopenLastProjectsOnStart()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger<IdeStarter>().error("Cannot reopen recent projects", e)
      false
    }

    if (!isOpened) {
      WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
    }
  }

  private suspend fun showWelcomeFrame(lifecyclePublisher: AppLifecycleListener): Boolean {
    val showWelcomeFrameTask = WelcomeFrame.prepareToShow() ?: return true
    serviceAsync<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
      showWelcomeFrameTask()
      runCatching {
        lifecyclePublisher.welcomeScreenDisplayed()
      }.getOrLogException(thisLogger())
    }
    return false
  }

  private suspend fun processUriParameter(uri: String, lifecyclePublisher: AppLifecycleListener) {
    val result = CommandLineProcessor.processProtocolCommand(uri)
    if (result.exitCode == ProtocolHandler.PLEASE_QUIT) {
      withContext(Dispatchers.EDT) {
        ApplicationManagerEx.getApplicationEx().exit(false, true)
      }
    }
    else if (result.exitCode != ProtocolHandler.PLEASE_NO_UI) {
      WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
    }
  }

  internal class StandaloneLightEditStarter : IdeStarter() {
    override suspend fun openProjectIfNeeded(args: List<String>,
                                             app: Application,
                                             asyncCoroutineScope: CoroutineScope,
                                             lifecyclePublisher: AppLifecycleListener) {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.openOrImportFilesAsync(list = filesToLoad, location = "MacMenu")
        args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      if (project != null) {
        return
      }

      val recentProjectManager = serviceAsync<RecentProjectsManager>()
      val isOpened = (if (recentProjectManager.willReopenProjectOnStart()) recentProjectManager.reopenLastProjectsOnStart() else true)
      if (!isOpened) {
        asyncCoroutineScope.launch(Dispatchers.EDT) {
          LightEditService.getInstance().showEditorWindow()
        }
      }
    }
  }
}

private suspend fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
  val currentDirectory = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
  @Suppress("SSBasedInspection")
  Logger.getInstance("#com.intellij.platform.ide.bootstrap.ApplicationLoader").info("ApplicationLoader.loadProject (cwd=${currentDirectory})")
  val result = CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory)
  if (result.hasError) {
    withContext(Dispatchers.EDT) {
      result.showError()
      ApplicationManager.getApplication().exit(true, true, false)
    }
  }
  return result.project
}

private fun CoroutineScope.postOpenUiTasks() {
  if (PluginManagerCore.isRunningFromSources()) {
    updateAppWindowIcon(JOptionPane.getRootFrame())
  }

  if (SystemInfoRt.isMac) {
    launch(CoroutineName("mac touchbar on app init")) {
      TouchbarSupport.onApplicationLoaded()
    }
  }
  else if (SystemInfoRt.isUnix && !SystemInfoRt.isMac && SystemInfo.isJetBrainsJvm) {
    launch(CoroutineName("input method disabling on Linux")) {
      disableInputMethodsIfPossible()
    }
  }

  launch {
    startSystemHealthMonitor()
  }
}

private suspend fun reportPluginErrors() {
  val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors().toMutableList()
  if (pluginErrors.isEmpty()) {
    return
  }

  withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
    val title = IdeBundle.message("title.plugin.error")
    val actions = linksToActions(pluginErrors)
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrors).toString()
    @Suppress("DEPRECATION")
    NotificationGroupManager.getInstance().getNotificationGroup(
      "Plugin Error").createNotification(title, content, NotificationType.ERROR)
      .setListener { notification, event ->
        notification.expire()
        PluginManagerMain.onEvent(event.description)
      }
      .addActions(actions)
      .notify(null)
  }
}

private fun linksToActions(errors: MutableList<HtmlChunk>): Collection<AnAction> {
  val link = "<a href=\""
  val actions = ArrayList<AnAction>()

  while (!errors.isEmpty()) {
    val builder = StringBuilder()
    errors[errors.lastIndex].appendTo(builder)
    val error = builder.toString()

    if (error.startsWith(link)) {
      val descriptionEnd = error.indexOf('"', link.length)
      val description = error.substring(link.length, descriptionEnd)
      val text = error.substring(descriptionEnd + 2, error.lastIndexOf("</a>"))
      errors.removeAt(errors.lastIndex)

      actions.add(NotificationAction.createSimpleExpiring(text) {
        PluginManagerMain.onEvent(description)
      })
    }
    else {
      break
    }
  }

  return actions
}
