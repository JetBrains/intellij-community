// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.AppUIUtil
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.beans.PropertyChangeListener
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ForkJoinPool
import javax.swing.JOptionPane

open class IdeStarter : ApplicationStarter {
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

  override fun isHeadless() = false

  override fun getCommandName(): String? = null

  final override fun getRequiredModality() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    throw UnsupportedOperationException("Use start(args)")
  }

  suspend fun start(args: List<String>) {
    val app = ApplicationManagerEx.getApplicationEx()
    assert(!app.isDispatchThread)

    coroutineScope {
      if (app.isLightEditMode && !app.isHeadlessEnvironment) {
        // In a light mode UI is shown very quickly, tab layout requires ActionManager, but it is forbidden to init ActionManager in EDT,
        // so, preload
        launch {
          ActionManager.getInstance()
        }
      }

      val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
      openProjectIfNeeded(args, app, lifecyclePublisher)

      launch { reportPluginErrors() }

      StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)
      lifecyclePublisher.appStarted()

      if (!app.isHeadlessEnvironment) {
        postOpenUiTasks(app)
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

    if (ApplicationManager.getApplication().isInternal) {
      UiInspectorAction.initGlobalInspector()
    }

    ForkJoinPool.commonPool().execute {
      LifecycleUsageTriggerCollector.onIdeStart()
    }

    if (uriToOpen != null || args.isNotEmpty() && args[0].contains(SCHEME_SEPARATOR)) {
      frameInitActivity.end()
      processUriParameter(uriToOpen ?: args[0], lifecyclePublisher)
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
      filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
      args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
      else -> null
    }

    when {
      project != null -> {
        return
      }
      willReopenRecentProjectOnStart -> {
        val isOpened = recentProjectManager.reopenLastProjectsOnStart()
        if (!isOpened) {
          WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
        }
      }
      else -> {
        WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
      }
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

  private fun processUriParameter(uri: String, lifecyclePublisher: AppLifecycleListener) {
    ApplicationManager.getApplication().invokeLater {
      CommandLineProcessor.processProtocolCommand(uri)
        .thenAccept {
          if (it.exitCode == ProtocolHandler.PLEASE_QUIT) {
            ApplicationManager.getApplication().invokeLater {
              ApplicationManagerEx.getApplicationEx().exit(false, true)
            }
          }
          else if (it.exitCode != ProtocolHandler.PLEASE_NO_UI) {
            WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
          }
        }
    }
  }

  internal class StandaloneLightEditStarter : IdeStarter() {
    override suspend fun openProjectIfNeeded(args: List<String>,
                                             app: ApplicationEx,
                                             lifecyclePublisher: AppLifecycleListener) {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
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

private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
  val currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
  @Suppress("SSBasedInspection")
  Logger.getInstance("#com.intellij.idea.ApplicationLoader").info("ApplicationLoader.loadProject (cwd=${currentDirectory})")
  val result = CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory)
  if (result.hasError) {
    ApplicationManager.getApplication().invokeAndWait {
      result.showErrorIfFailed()
      ApplicationManager.getApplication().exit(true, true, false)
    }
  }
  return result.project
}

private suspend fun postOpenUiTasks(app: Application) {
  coroutineScope {
    if (PluginManagerCore.isRunningFromSources()) {
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
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

    invokeLaterWithAnyModality("system dock menu") {
      SystemDock.updateMenu()
    }
    invokeLaterWithAnyModality("ScreenReader") {
      val generalSettings = GeneralSettings.getInstance()
      generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, app, PropertyChangeListener { e ->
        ScreenReader.setActive(e.newValue as Boolean)
      })
      ScreenReader.setActive(generalSettings.isSupportScreenReaders)
    }
  }
}

private suspend fun invokeLaterWithAnyModality(name: String, task: () -> Unit) {
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    runActivity(name, task = task)
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
