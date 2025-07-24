// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.customFrameDecorations.header.*
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isDecoratedMenu
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isFloatingMenuBarSupported
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isMenuButtonInToolbar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SelectedEditorFilePath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.blockingComputeMainActionGroups
import com.intellij.openapi.wm.impl.headertoolbar.computeMainActionGroups
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.menu.ActionAwareIdeMenuBar
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.createIdeMainMenuActionGroup
import com.intellij.platform.ide.menu.createMacMenuBar
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.MacMenuSettings
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.system.OS
import com.intellij.util.ui.JBUI
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*

private typealias MainToolbarActions = List<Pair<ActionGroup, HorizontalLayout.Group>>

/**
 * Helper class to contain the logic behind menu/toolbar presentation updates
 */
// hours spent untangling this: ~60
internal class ProjectFrameCustomHeaderHelper(
  app: Application,
  private val coroutineScope: CoroutineScope,
  frame: JFrame,
  private val frameDecorator: IdeFrameDecorator?,
  private val rootPane: IdeRootPane,
  private val isLightEdit: Boolean,
  mainMenuActionGroup: ActionGroup?,
) {
  private val frameHeaderHelper: FrameHeaderHelper

  private val isInFullScreen: Boolean
    get() = frameDecorator?.isInFullScreen == true

  private val toolbarCreator = ToolbarCreator(coroutineScope, frame, rootPane, ::isInFullScreen)

  private val toolbarVisibilityUpdateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    frameHeaderHelper = installCustomHeader(coroutineScope, frame, rootPane, mainMenuActionGroup, isLightEdit, ::isInFullScreen)
    frameHeaderHelper.init(frame, rootPane, coroutineScope)

    scheduleUpdateMainMenuVisibility()

    val toolbarHolder = frameHeaderHelper.toolbarHolder
    var toolbarInitJob: Job? = null
    if (toolbarHolder == null) {
      toolbarInitJob = coroutineScope.launch(rootTask() + ModalityState.any().asContextElement()) {
        withContext(Dispatchers.UI) {
          toolbarCreator.getOrCreateToolbar().isVisible = isToolbarVisible(UISettings.shadowInstance, isInFullScreen) {
            computeMainActionGroups()
          }
        }

        if (!isLightEdit && ExperimentalUI.isNewUI()) {
          // init of toolbar in window header is important to make as fast as possible
          // https://youtrack.jetbrains.com/issue/IDEA-323474
          span("toolbar init") {
            (toolbarCreator.getToolbarIfCreated() as MainToolbar).init()
          }
        }
      }
    }

    if (isLightEdit && ExperimentalUI.isNewUI()) {
      launchToolbarUpdate()
    }

    ComponentUtil.decorateWindowHeader(rootPane)

    app.messageBus.connect(coroutineScope).subscribe(UISettingsListener.TOPIC, UISettingsListener {
      ComponentUtil.decorateWindowHeader(rootPane)
      updateToolbarVisibility()
      updateScreenState(it, isInFullScreen)
    })

    if (frameDecorator != null && (frameHeaderHelper is FrameHeaderHelper.Decorated || frameHeaderHelper.isFloatingMenuBarSupported)) {
      rootPane.addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) {
        val fullScreenProperty = ClientProperty.isTrue(rootPane, IdeFrameDecorator.FULL_SCREEN)
        updateScreenState(UISettings.getInstance(), fullScreenProperty)
      }
      updateScreenState(UISettings.getInstance(), frameDecorator.isInFullScreen)
    }

    coroutineScope.launch(CoroutineName("MainToolbar visibility updates") + ModalityState.any().asContextElement()) {
      toolbarInitJob?.join() // to avoid races with init
      toolbarVisibilityUpdateRequests.collectLatest {
        val isToolbarVisible = isToolbarVisible(UISettings.shadowInstance, isInFullScreen) { computeMainActionGroups() }
        // This is more complicated than it seems.
        // There's one seemingly simple optimization: if we need to make the toolbar invisible, don't create it just for that.
        // But because toolbar creation is asynchronous, it can be in the process of creation already,
        // and when it's finally created, it appears out of nowhere, being initially visible by default.
        // The most typical case is this:
        // 1. This collector is executed with isToolbarVisible = true.
        // 2. Toolbar creation is initiated to make it visible.
        // 3. The collector is immediately canceled, and a new one is started with isToolbarVisible = false.
        // 4. The toolbar doesn't exist yet, so there's nothing to make invisible,
        // so if we're using a simple if-null-return solution, then the toolbar will become visible once it's created (IJPL-188106).
        // There are various hacky ways of dealing with it,
        // but to have the most robust solution, we need to keep visibility changes to one place
        // (excluding the initialization above, which is why we join the init job first).
        // Then it becomes relatively easy:
        // - if we need to make the toolbar visible, ensure it's created first (obviously!),
        // - otherwise, await its creation, but only if it was initiated already.
        withContext(Dispatchers.UI) {
          var toolbar = if (isToolbarVisible) {
            toolbarCreator.getOrCreateToolbar()
          }
          else {
            toolbarCreator.getToolbarIfCreatedOrCreating()
          }
          toolbar?.isVisible = isToolbarVisible
        }
      }
    }
  }

  fun isColorfulToolbar(): Boolean = frameHeaderHelper.toolbarHolder?.isColorfulToolbar() == true

  fun getCustomTitleBar(): WindowDecorations.CustomTitleBar? {
    val titlePane = (frameHeaderHelper as? FrameHeaderHelper.Decorated)?.customFrameTitlePane
    return (titlePane as? CustomHeader)?.customTitleBar ?: (titlePane as? MacToolbarFrameHeader)?.customTitleBar
  }

  fun launchToolbarUpdate() {
    val delegate = frameHeaderHelper.toolbarHolder
    if (delegate != null) {
      delegate.scheduleUpdateToolbar()
      return
    }

    updateToolbarVisibility()
  }

  private fun scheduleUpdateMainMenuVisibility() {
    val menuBar = rootPane.jMenuBar
    if (menuBar == null) {
      return
    }

    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      // don't show the Swing menu when a global (system) menu is presented
      val visible = if (MacMenuSettings.isSystemMenu || isInFullScreen) {
        true
      }
      else {
        val uiSettings = UISettings.shadowInstance
        !IdeFrameDecorator.isCustomDecorationActive() && uiSettings.showMainMenu && !hideNativeLinuxTitle(uiSettings) &&
        (!isMenuButtonInToolbar(uiSettings) || (ExperimentalUI.isNewUI() && isCompactHeader { computeMainActionGroups() }))
      }
      if (visible != menuBar.isVisible) {
        menuBar.isVisible = visible
      }
    }
  }

  private fun updateScreenState(uiSettings: UISettings, isFullScreen: Boolean) {
    if (frameHeaderHelper is FrameHeaderHelper.Decorated) {
      val wasCustomFrameHeaderVisible = frameHeaderHelper.customFrameTitlePane.getComponent().isVisible
      val isCustomFrameHeaderVisible = isCustomFrameHeaderVisible(uiSettings, isFullScreen) {
        blockingComputeMainActionGroups(CustomActionsSchema.getInstance())
      }
      frameHeaderHelper.customFrameTitlePane.getComponent().isVisible = isCustomFrameHeaderVisible
      if (wasCustomFrameHeaderVisible != isCustomFrameHeaderVisible) {
        frameHeaderHelper.toolbarHolder?.scheduleUpdateToolbar()
        updateToolbarVisibility()
      }
    }
    else if (OS.isGenericUnix()) {
      toolbarCreator.getToolbarIfCreated()?.isVisible = isToolbarVisible(uiSettings, isFullScreen) {
        blockingComputeMainActionGroups(CustomActionsSchema.getInstance())
      }
    }

    scheduleUpdateMainMenuVisibility()
  }

  private fun updateToolbarVisibility() {
    if (ExperimentalUI.isNewUI() && OS.CURRENT == OS.macOS) {
      return
    }
    check(toolbarVisibilityUpdateRequests.tryEmit(Unit))
  }

  fun setProject(project: Project) {
    (frameHeaderHelper as? FrameHeaderHelper.Decorated)?.selectedEditorFilePath?.project = project
  }

  fun launchMainMenuActionsUpdate() {
    if (frameHeaderHelper is FrameHeaderHelper.Decorated) {
      val customFrameTitlePane = frameHeaderHelper.customFrameTitlePane
      frameHeaderHelper.ideMenu.updateMenuActions(forceRebuild = false)
      // The menu bar is decorated, we update it indirectly.
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        customFrameTitlePane.updateMenuActions(forceRebuild = false)
        customFrameTitlePane.getComponent().repaint()
      }
    }
    else {
      val jMenuBar = rootPane.jMenuBar
      if (jMenuBar != null) {
        // no decorated menu bar, but there is a regular one, update it directly
        coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          (jMenuBar as ActionAwareIdeMenuBar).updateMenuActions(forceRebuild = false)
          jMenuBar.repaint()
        }
      }
    }
  }

  private inline fun isCustomFrameHeaderVisible(
    uiSettings: UISettings,
    isFullScreen: Boolean,
    mainToolbarActionSupplier: () -> MainToolbarActions,
  ): Boolean =
    !isFullScreen ||
    OS.CURRENT != OS.macOS && CustomWindowHeaderUtil.isToolbarInHeader(uiSettings, isFullscreen = true) ||
    OS.CURRENT == OS.macOS && !isCompactHeader(mainToolbarActionSupplier)

  private inline fun isToolbarVisible(
    uiSettings: UISettings,
    isFullScreen: Boolean,
    mainToolbarActionSupplier: () -> MainToolbarActions,
  ): Boolean {
    val isNewToolbar = ExperimentalUI.isNewUI()
    return isNewToolbar && !CustomWindowHeaderUtil.isToolbarInHeader(uiSettings, isFullScreen)
           && !isCompactHeader(mainToolbarActionSupplier)
           || !isNewToolbar && uiSettings.showMainToolbar
  }

  private inline fun isCompactHeader(mainToolbarActionSupplier: () -> MainToolbarActions): Boolean =
    isLightEdit || CustomWindowHeaderUtil.isCompactHeader(mainToolbarActionSupplier)
}

/**
 * A helper class to ensure that a toolbar is created only once or never.
 *
 * Provides helper methods to get the toolbar if:
 * - it was already completely created ([getToolbarIfCreated]),
 * - it was already created or its creation was already initiated elsewhere ([getToolbarIfCreatedOrCreating]),
 * - it doesn't matter if it was created or not, but it's definitely needed now ([getOrCreateToolbar]).
 */
private class ToolbarCreator(
  private val cs: CoroutineScope,
  private val frame: JFrame,
  private val rootPane: IdeRootPane,
  private val isFullScreen: () -> Boolean
) {
  private val lock = Mutex()
  private var toolbarCreationJob: Deferred<JComponent>? = null
  private val toolbar = AtomicReference<JComponent?>()

  fun getToolbarIfCreated(): JComponent? {
    return toolbar.get()
  }

  suspend fun getToolbarIfCreatedOrCreating(): JComponent? {
    val job = lock.withLock {
      toolbarCreationJob
    }
    return job?.await()
  }

  suspend fun getOrCreateToolbar(): JComponent {
    val job = lock.withLock {
      toolbarCreationJob ?: startNewJob()
    }
    return job.await()
  }

  private fun startNewJob(): Deferred<JComponent> {
    val newJob = cs.async(
      Dispatchers.UiWithModelAccess +
      ModalityState.any().asContextElement() +
      CoroutineName("Lazy MainToolbar computation")
    ) {
      val newToolbar = createToolbar(cs.childScope("MainToolbar"), frame, isFullScreen)
      toolbar.set(newToolbar)
      rootPane.installToolbar(newToolbar)
      newToolbar
    }
    toolbarCreationJob = newJob
    return newJob
  }
}

private suspend fun createToolbar(coroutineScope: CoroutineScope, frame: JFrame, isFullScreen: () -> Boolean): JComponent {
  if (ExperimentalUI.isNewUI()) {
    val toolbar = withContext(Dispatchers.EDT) {
      val toolbar = MainToolbar(
        coroutineScope = coroutineScope,
        frame = frame,
        isOpaque = true,
        background = InternalUICustomization.getInstance()?.getMainToolbarBackground(true) ?: JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true),
        isFullScreen
      )
      toolbar.border = JBUI.Borders.emptyLeft(5)
      toolbar
    }
    toolbar.init()
    return toolbar
  }
  else {
    // don't bother a client to know that old ui doesn't use coroutine scope
    coroutineScope.cancel()
    val group = CustomActionsSchema.getInstanceAsync().getCorrectedActionAsync(IdeActions.GROUP_MAIN_TOOLBAR)!!
    val toolBar = ActionManagerEx.getInstanceEx().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true)
    toolBar.targetComponent = null
    toolBar.layoutStrategy = ToolbarLayoutStrategy.WRAP_STRATEGY
    PopupHandler.installPopupMenu(toolBar.component, "MainToolbarPopupActions", "MainToolbarPopup")
    return toolBar.component
  }
}

private fun installCustomHeader(
  parentCs: CoroutineScope,
  frame: JFrame,
  rootPane: IdeRootPane,
  mainMenuActionGroup: ActionGroup?,
  isAlwaysCompact: Boolean,
  isFullScreen: () -> Boolean,
): FrameHeaderHelper {
  val uiSettings = UISettings.getInstance()
  val isDecoratedMenu = isDecoratedMenu(uiSettings)
  val isFloatingMenuBarSupported = isFloatingMenuBarSupported
  return if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
    createMacAwareMenuBar(parentCs.childScope(), frame, rootPane, mainMenuActionGroup)
    val headerHelper = FrameHeaderHelper.Undecorated(isFloatingMenuBarSupported = false)
    if (OS.isGenericUnix() && !isMenuButtonInToolbar(uiSettings)) {
      val menuBar = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup).apply {
        isOpaque = true
      }
      installMenuBar(rootPane, menuBar)
    }
    headerHelper
  }
  else {
    val headerHelper = if (isDecoratedMenu) {
      val selectedEditorFilePath: SelectedEditorFilePath?
      val ideMenu: ActionAwareIdeMenuBar
      val customFrameTitlePane = if (ExperimentalUI.isNewUI()) {
        selectedEditorFilePath = null
        ideMenu = createMacAwareMenuBar(parentCs.childScope(), frame, rootPane, mainMenuActionGroup)
        if (OS.CURRENT == OS.macOS) {
          MacToolbarFrameHeader(parentCs.childScope(), frame, rootPane, isAlwaysCompact)
        }
        else {
          ToolbarFrameHeader(parentCs.childScope(), frame, ideMenu as IdeJMenuBar, isAlwaysCompact, isFullScreen)
        }
      }
      else {
        CustomHeader.enableCustomHeader(frame)

        ideMenu = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup)
        selectedEditorFilePath = CustomDecorationPath(frame)
        MenuFrameHeader(frame, headerTitle = selectedEditorFilePath, ideMenu, isAlwaysCompact)
      }
      val headerHelper = FrameHeaderHelper.Decorated(
        customFrameTitlePane,
        selectedEditorFilePath,
        ideMenu,
        isFloatingMenuBarSupported,
        isAlwaysCompact,
        isFullScreen = isFullScreen)
      rootPane.installCustomFrameTitle(customFrameTitlePane.getComponent())
      headerHelper
    }
    else if (hideNativeLinuxTitle(uiSettings)) {
      val ideMenu = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup)
      val customFrameTitlePane = ToolbarFrameHeader(parentCs.childScope(), frame, ideMenu, isAlwaysCompact, isFullScreen)
      val headerHelper = FrameHeaderHelper.Decorated(
        customFrameTitlePane,
        selectedEditorFilePath = null,
        ideMenu,
        isFloatingMenuBarSupported = true,
        isAlwaysCompact,
        isFullScreen = isFullScreen
      )
      rootPane.installCustomFrameTitle(customFrameTitlePane.getComponent())
      headerHelper
    }
    else {
      FrameHeaderHelper.Undecorated(isFloatingMenuBarSupported = true)
    }

    if (isFloatingMenuBarSupported) {
      val menuBar = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup).apply {
        isOpaque = true
      }
      installMenuBar(rootPane, menuBar)
    }
    headerHelper
  }
}

private fun createMacAwareMenuBar(
  coroutineScope: CoroutineScope,
  frame: JFrame,
  rootPane: JRootPane,
  mainMenuActionGroup: ActionGroup?,
): ActionAwareIdeMenuBar {
  if (OS.CURRENT != OS.macOS) {
    return RootPaneUtil.createMenuBar(coroutineScope, frame, mainMenuActionGroup)
  }
  else if (Menu.isJbScreenMenuEnabled()) {
    return createMacMenuBar(coroutineScope, rootPane, frame) { mainMenuActionGroup ?: createIdeMainMenuActionGroup() }
  }
  else {
    val menuBar = RootPaneUtil.createMenuBar(coroutineScope, frame, mainMenuActionGroup)
    rootPane.jMenuBar = menuBar
    return menuBar
  }
}

private fun installMenuBar(rootPane: JRootPane, menuBar: JMenuBar) {
  rootPane.jMenuBar = menuBar
  rootPane.layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
}

private sealed interface FrameHeaderHelper {
  val toolbarHolder: ToolbarHolder?
  val ideMenu: ActionAwareIdeMenuBar?
  val isFloatingMenuBarSupported: Boolean

  fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {}

  class Undecorated(override val isFloatingMenuBarSupported: Boolean) : FrameHeaderHelper {
    override val toolbarHolder: ToolbarHolder?
      get() = null

    override val ideMenu: ActionAwareIdeMenuBar?
      get() = null

    override fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {
      ToolbarService.getInstance().setCustomTitleBar(frame, pane, onDispose = { runnable ->
        coroutineScope.coroutineContext.job.invokeOnCompletion {
          runnable.run()
        }
      })
    }
  }

  class Decorated(
    val customFrameTitlePane: MainFrameCustomHeader,
    val selectedEditorFilePath: SelectedEditorFilePath?,
    override val ideMenu: ActionAwareIdeMenuBar,
    override val isFloatingMenuBarSupported: Boolean,
    private val isLightEdit: Boolean,
    private val isFullScreen: () -> Boolean,
  ) : FrameHeaderHelper {
    override val toolbarHolder: ToolbarHolder?
      get() = (customFrameTitlePane as? ToolbarHolder)
        ?.takeIf {
          ExperimentalUI.isNewUI() && (CustomWindowHeaderUtil.isToolbarInHeader(UISettings.getInstance(), isFullScreen()) || isLightEdit)
        }
  }
}
