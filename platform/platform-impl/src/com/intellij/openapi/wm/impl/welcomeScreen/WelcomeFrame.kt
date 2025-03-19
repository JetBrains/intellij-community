// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.CommonBundle
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.internal.statistic.eventLog.getUiEventLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.bootstrap.hideSplashBeforeShow
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.menu.installAppMenuIfNeeded
import com.intellij.ui.BalloonLayout
import com.intellij.ui.BalloonLayoutImpl
import com.intellij.ui.DisposableWindow
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextAccessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.KeyStroke

class WelcomeFrame : JFrame(), IdeFrame, AccessibleContextAccessor, DisposableWindow {
  private val myScreen: WelcomeScreen
  private val myBalloonLayout: BalloonLayout
  private val listenerDisposable = Disposer.newDisposable()
  private var isDisposed = false

  init {
    hideSplashBeforeShow(this)
    val rootPane = getRootPane()
    val screen = createScreen(rootPane)
    val glassPane = IdeGlassPaneImpl(rootPane)
    setGlassPane(glassPane)
    glassPane.isVisible = false
    contentPane = screen.welcomePanel
    title = ApplicationNamesInfo.getInstance().fullProductName
    updateAppWindowIcon(this)
    ApplicationManager.getApplication().messageBus.connect(listenerDisposable).subscribe(ProjectManager.TOPIC,
                                                                                         object : ProjectManagerListener {
                                                                                           @Suppress("removal", "OVERRIDE_DEPRECATION")
                                                                                           override fun projectOpened(project: Project) {
                                                                                             dispose()
                                                                                           }
                                                                                         })
    myBalloonLayout = BalloonLayoutImpl(rootPane, JBUI.insets(8))
    myScreen = screen
    setupCloseAction(this)
    MnemonicHelper.init(this)
    myScreen.setupFrame(this)
    Disposer.register(ApplicationManager.getApplication(), ::dispose)
  }

  companion object {
    @JvmField
    val EP: ExtensionPointName<WelcomeFrameProvider> = ExtensionPointName("com.intellij.welcomeFrameProvider")
    const val DIMENSION_KEY: String = "WELCOME_SCREEN"

    private var instance: IdeFrame? = null

    private var touchbar: Disposable? = null

    @JvmStatic
    fun getInstance(): IdeFrame? = instance

    private fun saveLocation(location: Rectangle) {
      val middle = Point(location.x + location.width / 2, location.height / 2.also { location.y = it })
      DimensionService.getInstance().setLocation(DIMENSION_KEY, middle, null)
    }

    fun setupCloseAction(frame: JFrame) {
      frame.defaultCloseOperation = DO_NOTHING_ON_CLOSE
      frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) {
          if (ProjectUtilCore.getOpenProjects().isEmpty()) {
            val isActiveClone = CloneableProjectsService.getInstance().isCloneActive()
            if (isActiveClone) {
              val exitCode = Messages.showOkCancelDialog(ApplicationBundle.message("exit.confirm.prompt.tasks"),
                                                         ApplicationBundle.message("exit.confirm.title"),
                                                         ApplicationBundle.message("command.exit"),
                                                         CommonBundle.getCancelButtonText(),
                                                         Messages.getQuestionIcon())
              if (exitCode == Messages.CANCEL) {
                return
              }
            }
            frame.dispose()
            ApplicationManager.getApplication().exit()
          }
          else {
            frame.dispose()
          }
        }
      })
    }

    private fun createScreen(rootPane: JRootPane): WelcomeScreen {
      for (provider in WelcomeScreenProvider.EP_NAME.extensionList) {
        if (!provider.isAvailable) {
          continue
        }
        provider.createWelcomeScreen(rootPane)?.let {
          return it
        }
      }
      return NewWelcomeScreen()
    }

    fun resetInstance() {
      instance = null
      touchbar?.let {
        Disposer.dispose(it)
        touchbar = null
      }
    }

    @JvmStatic
    fun showNow() {
      prepareToShow()?.invoke()
    }

    @Internal
    fun prepareToShow(): (() -> Unit)? {
      if (instance != null) {
        return null
      }

      // ActionManager is used on Welcome Frame, but should be initialized in a pooled thread and not in EDT.
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        serviceAsync<ActionManager>()
      }

      return task@{
        if (instance != null) {
          return@task
        }

        val frame = EP.lazySequence().mapNotNull { it.createFrame() }.firstOrNull()
                    ?: throw IllegalStateException("No implementation of `com.intellij.welcomeFrameProvider` extension point")
        val jFrame = frame as JFrame
        registerKeyboardShortcuts(jFrame.rootPane)
        hideSplashBeforeShow(jFrame)
        jFrame.isVisible = true
        FUSProjectHotStartUpMeasurer.reportWelcomeScreenShown()
        installAppMenuIfNeeded(jFrame)
        instance = frame
        if (SystemInfoRt.isMac) {
          touchbar = TouchbarSupport.showWindowActions(frame.component)
        }
      }
    }

    private fun registerKeyboardShortcuts(rootPane: JRootPane) {
      val helpAction = ActionListener {
        getUiEventLogger().logClickOnHelpDialog(WelcomeFrame::class.java)
        HelpManager.getInstance().invokeHelp("welcome")
      }
      ActionUtil.registerForEveryKeyboardShortcut(rootPane, helpAction, CommonShortcuts.getContextHelp())
      rootPane.registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
    }

    @JvmOverloads
    @JvmStatic
    fun showIfNoProjectOpened(lifecyclePublisher: AppLifecycleListener? = null) {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode) {
        return
      }

      val show = prepareToShow() ?: return
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        val windowManager = serviceAsync<WindowManager>() as? WindowManagerImpl ?: return@launch
        blockingContext {
          windowManager.disposeRootFrame()
          if (windowManager.projectFrameHelpers.isEmpty()) {
            show()
            lifecyclePublisher?.welcomeScreenDisplayed()
          }
        }
      }
    }
  }

  override fun dispose() {
    saveLocation(bounds)
    super.dispose()
    Disposer.dispose(myScreen)
    Disposer.dispose(listenerDisposable)
    resetInstance()
    isDisposed = true
  }

  override fun isWindowDisposed(): Boolean = isDisposed

  override fun getStatusBar(): StatusBar? {
    val pane = contentPane
    return if (pane is JComponent) UIUtil.findComponentOfType(pane, IdeStatusBarImpl::class.java) else null
  }

  override fun getBalloonLayout(): BalloonLayout = myBalloonLayout

  override fun suggestChildFrameBounds(): Rectangle = bounds

  override fun getProject(): Project = ProjectManager.getInstance().defaultProject

  override fun setFrameTitle(title: String) {
    setTitle(title)
  }

  override fun getComponent(): JComponent = getRootPane()

  override fun getCurrentAccessibleContext(): AccessibleContext = accessibleContext
}