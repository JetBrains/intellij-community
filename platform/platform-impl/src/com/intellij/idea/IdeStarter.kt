// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.idea

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.diagnostic.runActivity
import com.intellij.diagnostic.runChild
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.internal.inspector.UiInspectorAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR
import com.intellij.util.ui.accessibility.ScreenReader
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
      val app = ApplicationManagerEx.getApplicationEx()
      val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
      openProjectIfNeeded(args = args, app = app, lifecyclePublisher = lifecyclePublisher)

      launch { reportPluginErrors() }

      StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)
      lifecyclePublisher.appStarted()

      if (!app.isHeadlessEnvironment) {
        postOpenUiTasks()
      }
    }
  }

  @OptIn(IntellijInternalApi::class)
  protected open suspend fun openProjectIfNeeded(args: List<String>,
                                                 app: ApplicationEx,
                                                 lifecyclePublisher: AppLifecycleListener) {
    val frameInitActivity = startActivity("frame initialization")
    frameInitActivity.runChild("app frame created callback") {
      lifecyclePublisher.appFrameCreated(args)
    }

    // must be after `AppLifecycleListener#appFrameCreated`, because some listeners can mutate the state of `RecentProjectsManager`
    if (app.isHeadlessEnvironment) {
      frameInitActivity.end()
      LifecycleUsageTriggerCollector.onIdeStart()
      return
    }

    if (app.isInternal) {
      @Suppress("DEPRECATION")
      app.coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        UiInspectorAction.initGlobalInspector()
      }
    }

    @Suppress("DEPRECATION")
    app.coroutineScope.launch {
      LifecycleUsageTriggerCollector.onIdeStart()
    }

    if (uriToOpen != null || args.isNotEmpty() && args.first().contains(SCHEME_SEPARATOR)) {
      frameInitActivity.end()
      processUriParameter(uriToOpen ?: args.first(), lifecyclePublisher)
      return
    }

    val recentProjectManager = RecentProjectsManager.getInstance()
    val willReopenRecentProjectOnStart = recentProjectManager.willReopenProjectOnStart()
    val willOpenProject = willReopenRecentProjectOnStart || !args.isEmpty() || !filesToLoad.isEmpty()
    val needToOpenProject = willOpenProject || showWelcomeFrame(lifecyclePublisher)
    frameInitActivity.end()

    if (!needToOpenProject) {
      return
    }

    val project = when {
      filesToLoad.isNotEmpty() -> ProjectUtil.openOrImportFilesAsync(filesToLoad, "IdeStarter")
      args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
      else -> null
    }

    if (project != null) {
      return
    }

    val isOpened = willReopenRecentProjectOnStart && try {
      recentProjectManager.reopenLastProjectsOnStart()
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

  private fun showWelcomeFrame(lifecyclePublisher: AppLifecycleListener): Boolean {
    val showWelcomeFrameTask = WelcomeFrame.prepareToShow() ?: return true
    ApplicationManager.getApplication().invokeLater {
      showWelcomeFrameTask.run()
      lifecyclePublisher.welcomeScreenDisplayed()
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
                                             app: ApplicationEx,
                                             lifecyclePublisher: AppLifecycleListener) {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.openOrImportFilesAsync(list = filesToLoad, location = "MacMenu")
        args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      if (project != null) {
        return
      }

      val recentProjectManager = RecentProjectsManager.getInstance()
      val isOpened = (if (recentProjectManager.willReopenProjectOnStart()) recentProjectManager.reopenLastProjectsOnStart() else true)
      if (!isOpened) {
        ApplicationManager.getApplication().invokeLater {
          LightEditService.getInstance().showEditorWindow()
        }
      }
    }
  }
}

private suspend fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
  val currentDirectory = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
  @Suppress("SSBasedInspection")
  Logger.getInstance("#com.intellij.idea.ApplicationLoader").info("ApplicationLoader.loadProject (cwd=${currentDirectory})")
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
    launch {
      runActivity("mac touchbar on app init") {
        TouchbarSupport.onApplicationLoaded()
      }
    }
  }
  else if (SystemInfoRt.isXWindow && SystemInfo.isJetBrainsJvm) {
    launch {
      runActivity("input method disabling on Linux") {
        disableInputMethodsIfPossible()
      }
    }
  }

  launch {
    // first apply in the scope of IdeStarter
    service<ScreenReaderStateManager>().apply()
  }

  launch {
    startSystemHealthMonitor()
  }
}

@Service(Service.Level.APP)
private class ScreenReaderStateManager(coroutineScope: CoroutineScope) {
  init {
    coroutineScope.launch {
      GeneralSettings.getInstance().propertyChangedFlow.collect {
        if (it == GeneralSettings.PropertyNames.supportScreenReaders) {
          apply()
        }
      }
    }
  }

  suspend fun apply() {
    withContext(Dispatchers.EDT) {
      ScreenReader.setActive(GeneralSettings.getInstance().isSupportScreenReaders)
    }
  }
}

private suspend fun reportPluginErrors() {
  val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors()
  if (pluginErrors.isEmpty()) {
    return
  }

  withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
    val title = IdeBundle.message("title.plugin.error")
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrors).toString()
    @Suppress("DEPRECATION")
    NotificationGroupManager.getInstance().getNotificationGroup(
      "Plugin Error").createNotification(title, content, NotificationType.ERROR)
      .setListener { notification, event ->
        notification.expire()
        PluginManagerMain.onEvent(event.description)
      }
      .notify(null)
  }
}
