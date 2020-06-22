// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.diagnostic.runChild
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.customize.CustomizeIDEWizardDialog
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerConfigurableProxy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.AppUIUtil
import com.intellij.ui.mac.touchbar.TouchBarsManager
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.EventQueue
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.JOptionPane

open class IdeStarter : ApplicationStarter {
  companion object {
    private var filesToLoad: List<File> = emptyList()
    private var wizardStepProvider: CustomizeIDEWizardStepsProvider? = null

    @JvmStatic
    fun openFilesOnLoading(value: List<File>) {
      filesToLoad = value
    }

    @JvmStatic
    fun setWizardStepsProvider(provider: CustomizeIDEWizardStepsProvider) {
      wizardStepProvider = provider
    }
  }

  override fun isHeadless() = false

  override fun getCommandName(): String? = null

  override fun getRequiredModality() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {
    if (Main.isLightEdit() && !Main.isHeadless()) {
      // In a light mode UI is shown very quickly, tab layout requires ActionManager but it is forbidden to init ActionManager in EDT,
      // so, preload
      AppExecutorUtil.getAppExecutorService().execute {
        ActionManager.getInstance()
      }
    }

    val frameInitActivity = StartUpMeasurer.startMainActivity("frame initialization")

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    // out of EDT
    val windowManager = WindowManagerEx.getInstanceEx()
    runInEdt {
      frameInitActivity.runChild("IdeEventQueue informing about WindowManager") {
        IdeEventQueue.getInstance().setWindowManager(windowManager)
      }
    }

    val app = ApplicationManager.getApplication()

    // temporary check until the JRE implementation has been checked and bundled
    if (java.lang.Boolean.getBoolean("ide.popup.enablePopupType")) {
      @Suppress("SpellCheckingInspection")
      System.setProperty("jbre.popupwindow.settype", "true")
    }

    val isStandaloneLightEdit = PlatformUtils.getPlatformPrefix() == "LightEdit"
    val needToOpenProject: Boolean
    if (isStandaloneLightEdit) {
      needToOpenProject = true
    }
    else {
      val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
      frameInitActivity.runChild("app frame created callback") {
        lifecyclePublisher.appFrameCreated(args)
      }

      // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
      if (app.isHeadlessEnvironment) {
        needToOpenProject = false
      }
      else {
        val willOpenProject = args.isNotEmpty() || filesToLoad.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart()
        needToOpenProject = showWizardAndWelcomeFrame(lifecyclePublisher, willOpenProject)
      }

      frameInitActivity.end()

      NonUrgentExecutor.getInstance().execute {
        LifecycleUsageTriggerCollector.onIdeStart()
      }
    }

    val project = when {
      !needToOpenProject -> null
      filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFileList(null, filesToLoad, "MacMenu")
      args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
      else -> null
    }

    app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project)

    if (needToOpenProject && project == null && !JetBrainsProtocolHandler.appStartedWithCommand()) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      var openLightEditFrame = isStandaloneLightEdit
      if (recentProjectManager.willReopenProjectOnStart()) {
        if (recentProjectManager.reopenLastProjectsOnStart()) {
          openLightEditFrame = false
        }
        else if (!openLightEditFrame) {
          WelcomeFrame.showIfNoProjectOpened()
        }
      }

      // due to historical reasons, not safe to show welcome screen if willReopenProjectOnStart returns false, so, not possible to extract common branch
      if (openLightEditFrame) {
        ApplicationManager.getApplication().invokeLater {
          LightEditService.getInstance().showEditorWindow()
        }
      }
    }

    reportPluginError()

    if (!app.isHeadlessEnvironment) {
      postOpenUiTasks(app)
    }

    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)

    if (PluginManagerCore.isRunningFromSources() && !app.isHeadlessEnvironment) {
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
    }
  }

  private fun showWizardAndWelcomeFrame(lifecyclePublisher: AppLifecycleListener, willOpenProject: Boolean): Boolean {
    val shouldShowWelcomeFrame = !willOpenProject || JetBrainsProtocolHandler.getCommand() != null
    val doShowWelcomeFrame = if (shouldShowWelcomeFrame) WelcomeFrame.prepareToShow() else null
    val showWelcomeFrame = if (doShowWelcomeFrame == null) {
      null
    }
    else {
      Runnable {
        runInEdt {
          doShowWelcomeFrame.run()
        }
        lifecyclePublisher.welcomeScreenDisplayed()
      }
    }
    wizardStepProvider?.let { wizardStepProvider ->
      var done = false
      runInEdt {
        val wizardDialog = object : CustomizeIDEWizardDialog(wizardStepProvider, null, false, true) {
          override fun doOKAction() {
            super.doOKAction()
            showWelcomeFrame?.run()
          }
        }

        if (wizardDialog.showIfNeeded()) {
          done = true
        }
      }

      if (done) {
        return false
      }
    }

    if (showWelcomeFrame == null) {
      return true
    }

    showWelcomeFrame.run()
    return false
  }
}

private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
  val currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
  Logger.getInstance("#com.intellij.idea.ApplicationLoader").info("ApplicationLoader.loadProject (cwd=${currentDirectory})")
  val result = CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory)
  if (result.hasError) {
    ApplicationManager.getApplication().invokeLater {
      result.showErrorIfFailed()
    }
  }
  return result.project
}

private fun postOpenUiTasks(app: Application) {
  if (SystemInfo.isMac) {
    NonUrgentExecutor.getInstance().execute {
      runActivity("mac touchbar on app init") {
        TouchBarsManager.onApplicationInitialized()
        if (TouchBarsManager.isTouchBarAvailable()) {
          CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, IdeBundle.message("settings.menus.group.touch.bar"))
        }
      }
    }
  }
  else if (SystemInfo.isXWindow && SystemInfo.isJetBrainsJvm) {
    NonUrgentExecutor.getInstance().execute {
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

private fun reportPluginError() {
  val pluginError = PluginManagerCore.ourPluginError ?: return
  PluginManagerCore.ourPluginError = null

  ApplicationManager.getApplication().invokeLater({
    val title = IdeBundle.message("title.plugin.error")
    Notification(NotificationGroup.createIdWithTitle("Plugin Error", title),
                 title, pluginError, NotificationType.ERROR) { notification, event ->
      notification.expire()

      val description = event.description
      if (PluginManagerCore.EDIT == description) {
        val ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null)
        PluginManagerConfigurableProxy.showPluginConfigurable(ideFrame?.component, null)
        return@Notification
      }

      if (PluginManagerCore.ourPluginsToDisable != null && PluginManagerCore.DISABLE == description) {
        DisabledPluginsState.enablePluginsById(PluginManagerCore.ourPluginsToDisable, false)
      }
      else if (PluginManagerCore.ourPluginsToEnable != null && PluginManagerCore.ENABLE == description) {
        DisabledPluginsState.enablePluginsById(PluginManagerCore.ourPluginsToEnable, true)
        PluginManagerMain.notifyPluginsUpdated(null)
      }

      PluginManagerCore.ourPluginsToEnable = null
      PluginManagerCore.ourPluginsToDisable = null
    }.notify(null)
  }, ModalityState.NON_MODAL)
}