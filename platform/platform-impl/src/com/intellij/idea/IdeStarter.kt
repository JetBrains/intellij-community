// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.*
import com.intellij.ide.customize.CommonCustomizeIDEWizardDialog
import com.intellij.ide.customize.CustomizeIDEWizardDialog
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
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
import java.nio.file.Path
import javax.swing.JOptionPane

open class IdeStarter : ApplicationStarter {
  companion object {
    private var filesToLoad: List<Path> = emptyList()
    private var wizardStepProvider: CustomizeIDEWizardStepsProvider? = null

    @JvmStatic
    fun openFilesOnLoading(value: List<Path>) {
      filesToLoad = value
    }

    @JvmStatic
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
    app.invokeLater {
      runMainActivity("IdeEventQueue informing about WindowManager") {
        IdeEventQueue.getInstance().setWindowManager(windowManager)
      }
    }

    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    val isStandaloneLightEdit = PlatformUtils.getPlatformPrefix() == "LightEdit"
    val needToOpenProject: Boolean
    if (isStandaloneLightEdit) {
      needToOpenProject = true
    }
    else {
      frameInitActivity.runChild("app frame created callback") {
        lifecyclePublisher.appFrameCreated(args)
      }

      // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
      if (app.isHeadlessEnvironment) {
        needToOpenProject = false
      }
      else {
        val willOpenProject = (args.isNotEmpty() || filesToLoad.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart())
                              && JetBrainsProtocolHandler.getCommand() == null
        needToOpenProject = showWizardAndWelcomeFrame(lifecyclePublisher, willOpenProject)
      }

      frameInitActivity.end()

      NonUrgentExecutor.getInstance().execute {
        LifecycleUsageTriggerCollector.onIdeStart()
      }
    }

    val project = when {
      !needToOpenProject -> null
      filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFiles(null, filesToLoad, "MacMenu")
      args.isNotEmpty() -> loadProjectFromExternalCommandLine(args)
      else -> null
    }

    lifecyclePublisher.appStarting(project)

    if (needToOpenProject && project == null && !JetBrainsProtocolHandler.appStartedWithCommand()) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      var openLightEditFrame = isStandaloneLightEdit
      if (recentProjectManager.willReopenProjectOnStart()) {
        if (recentProjectManager.reopenLastProjectsOnStart()) {
          openLightEditFrame = false
        }
        else if (!isStandaloneLightEdit) {
          WelcomeFrame.showIfNoProjectOpened()
        }
      }

      if (openLightEditFrame) {
        ApplicationManager.getApplication().invokeLater {
          LightEditService.getInstance().showEditorWindow()
        }
      }
    }

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

  private fun showWizardAndWelcomeFrame(lifecyclePublisher: AppLifecycleListener, willOpenProject: Boolean): Boolean {
    val doShowWelcomeFrame = if (willOpenProject) null else WelcomeFrame.prepareToShow()
    // do not show Customize IDE Wizard [IDEA-249516]
    val wizardStepProvider = wizardStepProvider
    if (wizardStepProvider == null || System.getProperty("idea.show.customize.ide.wizard")?.toBoolean() != true) {
      if (doShowWelcomeFrame == null) {
        return true
      }

      ApplicationManager.getApplication().invokeLater {
        doShowWelcomeFrame.run()
        lifecyclePublisher.welcomeScreenDisplayed()
      }
      return false
    }

    // temporary until 211 release
    val stepDialogName = System.getProperty("idea.temp.change.ide.wizard")
                         ?: ApplicationInfoImpl.getShadowInstance().customizeIDEWizardDialog
    ApplicationManager.getApplication().invokeLater {
      val wizardDialog: CommonCustomizeIDEWizardDialog?
      if (stepDialogName.isNullOrBlank()) {
        wizardDialog = CustomizeIDEWizardDialog(wizardStepProvider, null, false, true)
      }
      else {
        wizardDialog = try {
          Class.forName(stepDialogName).getConstructor(StartupUtil.AppStarter::class.java)
            .newInstance(null) as CommonCustomizeIDEWizardDialog
        }
        catch (e: Throwable) {
          Main.showMessage(BootstrapBundle.message("bootstrap.error.title.configuration.wizard.failed"), e)
          null
        }
      }

      wizardDialog?.showIfNeeded()

      if (doShowWelcomeFrame != null) {
        doShowWelcomeFrame.run()
        lifecyclePublisher.welcomeScreenDisplayed()
      }
    }
    return false
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
    NonUrgentExecutor.getInstance().execute {
      runActivity("mac touchbar on app init") {
        TouchBarsManager.onApplicationInitialized()
        if (TouchBarsManager.isTouchBarAvailable()) {
          CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, IdeBundle.message("settings.menus.group.touch.bar"))
        }
      }
    }
  }
  else if (SystemInfoRt.isXWindow && SystemInfo.isJetBrainsJvm) {
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

private fun reportPluginErrors() {
  val pluginErrors = PluginManagerCore.getAndClearPluginLoadingErrors()
  if (pluginErrors.isEmpty()) {
    return
  }

  ApplicationManager.getApplication().invokeLater({
    val title = IdeBundle.message("title.plugin.error")
    val content = HtmlBuilder().appendWithSeparators(HtmlChunk.p(), pluginErrors).toString()
    Notification(NotificationGroup.createIdWithTitle("Plugin Error", title), title, content, NotificationType.ERROR) { notification, event ->
      notification.expire()

      val description = event.description
      if (PluginManagerCore.EDIT == description) {
        PluginManagerConfigurable.showPluginConfigurable(
          WindowManagerEx.getInstanceEx().findFrameFor(null)?.component,
          null,
          emptyList(),
        )
        return@Notification
      }

      if (PluginManagerCore.ourPluginsToDisable != null && PluginManagerCore.DISABLE == description) {
        DisabledPluginsState.enablePluginsById(PluginManagerCore.ourPluginsToDisable, false)
      }
      else if (PluginManagerCore.ourPluginsToEnable != null && PluginManagerCore.ENABLE == description) {
        DisabledPluginsState.enablePluginsById(PluginManagerCore.ourPluginsToEnable, true)
        @Suppress("SSBasedInspection")
        PluginManagerMain.notifyPluginsUpdated(null)
      }

      PluginManagerCore.ourPluginsToEnable = null
      PluginManagerCore.ourPluginsToDisable = null
    }.notify(null)
  }, ModalityState.NON_MODAL)
}