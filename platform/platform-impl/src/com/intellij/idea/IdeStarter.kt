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
import com.intellij.ide.plugins.PluginManagerConfigurableProxy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
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
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.EventQueue
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.util.*
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

  private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
    val currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
    Logger.getInstance("#com.intellij.idea.ApplicationLoader").info("ApplicationLoader.loadProject (CWD=${currentDirectory})")
    return CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory).first
  }

  override fun main(args: Array<String>) {
    main(args.toList())
  }

  internal fun main(args: List<String>) {
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

    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    frameInitActivity.runChild("app frame created callback") {
      lifecyclePublisher.appFrameCreated(args)
    }

    // temporary check until the JRE implementation has been checked and bundled
    if (java.lang.Boolean.getBoolean("ide.popup.enablePopupType")) {
      @Suppress("SpellCheckingInspection")
      System.setProperty("jbre.popupwindow.settype", "true")
    }

    // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
    val willOpenProject = args.isNotEmpty() || filesToLoad.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart()
    val needToOpenProject = showWizardAndWelcomeFrame(lifecyclePublisher, willOpenProject)

    frameInitActivity.end()

    NonUrgentExecutor.getInstance().execute {
      LifecycleUsageTriggerCollector.onIdeStart()
    }

    val project = when {
      !needToOpenProject -> null
      filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFileList(null, filesToLoad, "MacMenu")
      args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
      else -> null
    }

    app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project)

    if (needToOpenProject && project == null && RecentProjectsManager.getInstance().willReopenProjectOnStart() && !JetBrainsProtocolHandler.appStartedWithCommand()) {
      RecentProjectsManager.getInstance().reopenLastProjectsOnStart()
    }

    app.invokeLater {
      reportPluginError()
    }

    if (!app.isHeadlessEnvironment) {
      postOpenUiTasks(app)
    }

    StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_STARTED)

    if (PluginManagerCore.isRunningFromSources()) {
      NonUrgentExecutor.getInstance().execute {
        AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
      }
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
  if (PluginManagerCore.ourPluginError == null) {
    return
  }

  val title = IdeBundle.message("title.plugin.error")
  Notifications.Bus.notify(Notification(title, title, PluginManagerCore.ourPluginError, NotificationType.ERROR) { notification, event ->
    notification.expire()

    val description = event.description
    if (PluginManagerCore.EDIT == description) {
      val ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null)
      PluginManagerConfigurableProxy.showPluginConfigurable(ideFrame?.component, null)
      return@Notification
    }

    val disabledPlugins = LinkedHashSet(PluginManagerCore.disabledPlugins())
    if (PluginManagerCore.ourPluginsToDisable != null && PluginManagerCore.DISABLE == description) {
      disabledPlugins.addAll(PluginManagerCore.ourPluginsToDisable)
    }
    else if (PluginManagerCore.ourPluginsToEnable != null && PluginManagerCore.ENABLE == description) {
      disabledPlugins.removeAll(PluginManagerCore.ourPluginsToEnable)
      PluginManagerMain.notifyPluginsUpdated(null)
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disabledPlugins, false)
    }
    catch (ignore: IOException) { }

    PluginManagerCore.ourPluginsToEnable = null
    PluginManagerCore.ourPluginsToDisable = null
  })

  PluginManagerCore.ourPluginError = null
}