// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty")
package com.intellij.idea

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.diagnostic.runActivity
import com.intellij.diagnostic.runChild
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.internal.inspector.UiInspectorAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.*
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
    private var wizardStepProvider: CustomizeIDEWizardStepsProvider? = null

    fun openFilesOnLoading(value: List<Path>) {
      filesToLoad = value
    }

    fun setWizardStepsProvider(provider: CustomizeIDEWizardStepsProvider) {
      wizardStepProvider = provider
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

  protected open fun openProjectIfNeeded(args: List<String>,
                                         app: ApplicationEx,
                                         lifecyclePublisher: AppLifecycleListener): CompletableFuture<*> {
    val frameInitActivity = startActivity("frame initialization")
    frameInitActivity.runChild("app frame created callback") {
      lifecyclePublisher.appFrameCreated(args)
    }

    // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
    if (app.isHeadlessEnvironment) {
      frameInitActivity.end()

      LifecycleUsageTriggerCollector.onIdeStart()
      @Suppress("DEPRECATION")
      lifecyclePublisher.appStarting(null)
      return CompletableFuture.completedFuture(null)
    }

    if (ApplicationManager.getApplication().isInternal) {
      UiInspectorAction.initGlobalInspector()
    }

    if (JetBrainsProtocolHandler.appStartedWithCommand()) {
      val needToOpenProject = showWelcomeFrame(lifecyclePublisher, willOpenProject = false)
      frameInitActivity.end()
      LifecycleUsageTriggerCollector.onIdeStart()

      val project = when {
        !needToOpenProject -> null
        !filesToLoad.isEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
        !args.isEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }
      @Suppress("DEPRECATION")
      lifecyclePublisher.appStarting(project)
    }
    else {
      val recentProjectManager = RecentProjectsManager.getInstance()
      val willReopenRecentProjectOnStart = recentProjectManager.willReopenProjectOnStart()
      val willOpenProject = willReopenRecentProjectOnStart || !args.isEmpty() || !filesToLoad.isEmpty()
      val needToOpenProject = showWelcomeFrame(lifecyclePublisher, willOpenProject)
      frameInitActivity.end()
      ForkJoinPool.commonPool().execute {
        LifecycleUsageTriggerCollector.onIdeStart()
      }

      if (!needToOpenProject) {
        @Suppress("DEPRECATION")
        lifecyclePublisher.appStarting(null)
        return CompletableFuture.completedFuture(null)
      }

      val project = when {
        !filesToLoad.isEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
        !args.isEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }
      @Suppress("DEPRECATION")
      lifecyclePublisher.appStarting(project)

      if (project == null) {
        return if (willReopenRecentProjectOnStart) {
          recentProjectManager.reopenLastProjectsOnStart()
            .thenAccept { isOpened ->
              if (!isOpened) {
                WelcomeFrame.showIfNoProjectOpened()
              }
            }
        }
        else {
          CompletableFuture.completedFuture(null).thenRun {
            WelcomeFrame.showIfNoProjectOpened()
          }
        }
      }
    }
    return CompletableFuture.completedFuture(null)
  }

  private fun showWelcomeFrame(lifecyclePublisher: AppLifecycleListener, willOpenProject: Boolean): Boolean {
    val doShowWelcomeFrame = if (willOpenProject) null else WelcomeFrame.prepareToShow()

    if (doShowWelcomeFrame == null) return true

    ApplicationManager.getApplication().invokeLater {
      doShowWelcomeFrame.run()
      lifecyclePublisher.welcomeScreenDisplayed()
    }
    return false
  }

  internal class StandaloneLightEditStarter : IdeStarter() {
    override fun openProjectIfNeeded(args: List<String>,
                                     app: ApplicationEx,
                                     lifecyclePublisher: AppLifecycleListener): CompletableFuture<*> {
      val project = when {
        !filesToLoad.isEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
        !args.isEmpty() -> loadProjectFromExternalCommandLine(args)
        else -> null
      }

      if (project != null || JetBrainsProtocolHandler.appStartedWithCommand()) {
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
    Notification(NotificationGroup.createIdWithTitle("Plugin Error", title), title, content, NotificationType.ERROR)
      .setListener { notification, event ->
        notification.expire()
        PluginManagerMain.onEvent(event.description)
      }
      .notify(null)
  }, ModalityState.NON_MODAL)
}
