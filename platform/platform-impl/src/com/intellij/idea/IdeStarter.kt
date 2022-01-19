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
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
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
import java.awt.EventQueue
import java.beans.PropertyChangeListener
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import javax.swing.JOptionPane

open class IdeStarter : ApplicationStarter {
  companion object {
    private var filesToLoad: List<Path> = Collections.emptyList()
    private var uriToOpen: String? = null

    @JvmStatic fun openFilesOnLoading(value: List<Path>) {
      filesToLoad = value
    }

    @JvmStatic fun openUriOnLoading(value: String) {
      uriToOpen = value
    }
  }

  override fun isHeadless() = false

  override fun getCommandName(): String? = null

  final override fun getRequiredModality() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    val app = ApplicationManagerEx.getApplicationEx()
    assert(!app.isDispatchThread)

    if (app.isLightEditMode && !app.isHeadlessEnvironment) {
      // In a light mode UI is shown very quickly, tab layout requires ActionManager, but it is forbidden to init ActionManager in EDT,
      // so, preload
      ForkJoinPool.commonPool().execute {
        ActionManager.getInstance()
      }
    }

    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    openProjectIfNeeded(args, app, lifecyclePublisher).thenRun {
      reportPluginErrors()

      if (!app.isHeadlessEnvironment) {
        postOpenUiTasks(app)
      }

      StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)
      lifecyclePublisher.appStarted()

      if (!app.isHeadlessEnvironment && PluginManagerCore.isRunningFromSources()) {
        AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
      }
    }
  }

  protected open fun openProjectIfNeeded(args: List<String>, app: ApplicationEx, lifecyclePublisher: AppLifecycleListener): CompletableFuture<*> {
    val frameInitActivity = startActivity("frame initialization")
    frameInitActivity.runChild("app frame created callback") {
      lifecyclePublisher.appFrameCreated(args)
    }

    // must be after `AppLifecycleListener#appFrameCreated`, because some listeners can mutate the state of `RecentProjectsManager`
    if (app.isHeadlessEnvironment) {
      frameInitActivity.end()
      LifecycleUsageTriggerCollector.onIdeStart()
      return CompletableFuture.completedFuture(null)
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
      return CompletableFuture.completedFuture(null)
    }
    else {
      val recentProjectManager = RecentProjectsManager.getInstance()
      val willReopenRecentProjectOnStart = recentProjectManager.willReopenProjectOnStart()
      val willOpenProject = willReopenRecentProjectOnStart || !args.isEmpty() || !filesToLoad.isEmpty()
      val needToOpenProject = willOpenProject || showWelcomeFrame(lifecyclePublisher)
      frameInitActivity.end()

      if (!needToOpenProject) {
        return CompletableFuture.completedFuture(null)
      }

      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
        args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      return when {
        project != null -> {
          CompletableFuture.completedFuture(null)
        }
        willReopenRecentProjectOnStart -> {
          recentProjectManager.reopenLastProjectsOnStart().thenAccept { isOpened ->
            if (!isOpened) {
              WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
            }
          }
        }
        else -> {
          CompletableFuture.completedFuture(null).thenRun {
            WelcomeFrame.showIfNoProjectOpened(lifecyclePublisher)
          }
        }
      }
    }
  }

  private fun showWelcomeFrame(lifecyclePublisher: AppLifecycleListener): Boolean {
    val showWelcomeFrameTask = WelcomeFrame.prepareToShow()
    if (showWelcomeFrameTask == null) {
      return true
    }

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
    override fun openProjectIfNeeded(args: List<String>,
                                     app: ApplicationEx,
                                     lifecyclePublisher: AppLifecycleListener): CompletableFuture<*> {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
        args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      if (project != null) {
        return CompletableFuture.completedFuture(null)
      }

      val recentProjectManager = RecentProjectsManager.getInstance()
      return (if (recentProjectManager.willReopenProjectOnStart()) recentProjectManager.reopenLastProjectsOnStart()
              else CompletableFuture.completedFuture(true))
        .thenAccept { isOpened ->
          if (!isOpened) {
            ApplicationManager.getApplication().invokeLater {
              LightEditService.getInstance().showEditorWindow()
            }
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

private fun postOpenUiTasks(app: Application) {
  if (SystemInfoRt.isMac) {
    ForkJoinPool.commonPool().execute {
      runActivity("mac touchbar on app init") {
        TouchbarSupport.onApplicationLoaded()
      }
    }
  }
  else if (SystemInfoRt.isXWindow && SystemInfo.isJetBrainsJvm) {
    ForkJoinPool.commonPool().execute {
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

private fun invokeLaterWithAnyModality(name: String, task: () -> Unit) {
  EventQueue.invokeLater {
    runActivity(name, task = task)
  }
}

private fun reportPluginErrors() {
  val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors()
  if (pluginErrors.isEmpty()) {
    return
  }

  ApplicationManager.getApplication().invokeLater({
    val title = IdeBundle.message("title.plugin.error")
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrors).toString()
    NotificationGroupManager.getInstance().getNotificationGroup("Plugin Error").createNotification(title, content, NotificationType.ERROR)
      .setListener { notification, event ->
        notification.expire()
        PluginManagerMain.onEvent(event.description)
      }
      .notify(null)
  }, ModalityState.NON_MODAL)
}
