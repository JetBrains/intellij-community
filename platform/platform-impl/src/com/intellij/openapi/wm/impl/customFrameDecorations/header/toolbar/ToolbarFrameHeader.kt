// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxIconThemeConfiguration
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxResizableCustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isCompactHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.HEADER_HEIGHT_DFM
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath.SimpleCustomDecorationPathComponent
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.computeMainActionGroups
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGapsX
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.*
import java.awt.GridBagConstraints.WEST
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

private enum class ShowMode {
  MENU, TOOLBAR
}

internal class ToolbarFrameHeader(
  private val coroutineScope: CoroutineScope,
  frame: JFrame,
  private val ideMenuBar: IdeJMenuBar,
  private val isAlwaysCompact: Boolean,
  private val isFullScreen: () -> Boolean,
) : FrameHeader(frame), UISettingsListener, ToolbarHolder, MainFrameCustomHeader {
  private val ideMenuHelper = IdeMenuHelper(menu = ideMenuBar, coroutineScope = coroutineScope)
  private val menuBarHeaderTitle = SimpleCustomDecorationPathComponent(frame = frame, isGrey = true).apply {
    isOpaque = false
  }
  private val menuBarContainer = createMenuBarContainer()
  private val mainMenuButton = MainMenuButton(coroutineScope)
  private var toolbar: MainToolbar? = null
  private val toolbarPlaceholder = createToolbarPlaceholder()
  private val headerContent = createHeaderContent()
  private val expandableMenu = ExpandableMenu(headerContent = headerContent, coroutineScope = coroutineScope.childScope(), frame) { !isCompactHeader }
  private val toolbarHeaderTitle = SimpleCustomDecorationPathComponent(frame = frame).apply {
    isOpaque = false
  }

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var currentContentState: WindowButtonsConfiguration.State? = null

  @Volatile
  private var isCompactHeader: Boolean

  init {
    // color full toolbar
    isOpaque = false
    isCompactHeader = isAlwaysCompact || isCompactHeader(UISettings.getInstance())

    mainMenuButton.expandableMenu = expandableMenu
    layout = object : GridBagLayout() {
      override fun preferredLayoutSize(parent: Container?): Dimension {
        val size = super.preferredLayoutSize(parent)
        size.height = CustomWindowHeaderUtil.getPreferredWindowHeaderHeight(isCompactHeader = isCompactHeader)
        return size
      }
    }

    productIcon.border = JBUI.Borders.empty(V, 0, V, 0)

    fillContent(WindowButtonsConfiguration.getInstance()?.state)
    WindowButtonsConfiguration.getInstance()?.let {
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        it.stateFlow.collect { value ->
          // Skip initial call
          if (currentContentState !== value) {
            fillContent(value)
            (buttonPanes as? LinuxResizableCustomFrameButtons)?.fillContent(value)
          }
        }
      }
    }

    updateIconTheme(LinuxIconThemeConfiguration.getInstance()?.state?.iconTheme)
    LinuxIconThemeConfiguration.getInstance()?.let {
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        it.stateFlow.collect { value ->
          updateIconTheme(value?.iconTheme)
        }
      }
    }

    setCustomFrameTopBorder(isTopNeeded = { false }, isBottomNeeded = { mode == ShowMode.MENU })

    updateMenuBar()
    customTitleBar?.let {
      CustomWindowHeaderUtil.configureCustomTitleBar(isCompactHeader = isCompactHeader, customTitleBar = it, frame = frame)
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      updateRequests.emit(Unit)
      updateRequests.collect {
        withContext(ModalityState.any().asContextElement()) {
          withContext(Dispatchers.EDT) {
            updateLayout()
          }

          val compactHeader = isAlwaysCompact || isCompactHeader(UISettings.getInstance(), { computeMainActionGroups() })

          when (mode) {
            ShowMode.TOOLBAR -> doUpdateToolbar(compactHeader)
            ShowMode.MENU -> {
              withContext(Dispatchers.EDT) {
                toolbar?.removeComponentListener(contentResizeListener)
                toolbarPlaceholder.removeAll()
                toolbarPlaceholder.revalidate()
                toolbar = null
              }
            }
          }
          isCompactHeader = compactHeader

          withContext(Dispatchers.EDT) {
            buttonPanes?.isCompactMode = isCompactHeader
            val size = if (isCompactHeader) {
              JBDimension(HEADER_HEIGHT_DFM, HEADER_HEIGHT_DFM)
            }
            else {
              ActionToolbar.experimentalToolbarMinimumButtonSize()
            }
            mainMenuButton.button.setMinimumButtonSize(size)
            if (mode == ShowMode.MENU) {
              menuBarHeaderTitle.isVisible = isCompactHeader
            }
            repaint()
          }
        }
      }
    }
  }

  override fun doLayout() {
    super.doLayout()

    val height = height
    if (height != 0) {
      customTitleBar?.height = height.toFloat()
    }
  }

  override fun removeNotify() {
    super.removeNotify()
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      coroutineScope.cancel()
    }
  }

  private fun fillContent(state: WindowButtonsConfiguration.State?) {
    currentContentState = state

    removeAll()

    val gb = GridBag().anchor(WEST)
    if (state == null || state.rightPosition) {
      add(productIcon, gb.nextLine().next().anchor(WEST).insetLeft(H))
      add(headerContent, gb.next().fillCell().anchor(GridBagConstraints.CENTER).weightx(1.0).weighty(1.0))
      buttonPanes?.let { add(wrap(it.getContent()), gb.next().anchor(GridBagConstraints.EAST)) }
      updateHeaderContentBorder(true)
    }
    else {
      val buttonPanes = buttonPanes
      if (buttonPanes != null) {
        add(wrap(buttonPanes.getContent()), gb.nextLine().next().anchor(WEST))
      }
      updateHeaderContentBorder(buttonPanes == null)
      add(headerContent, gb.next().fillCell().anchor(GridBagConstraints.CENTER).weightx(1.0).weighty(1.0))
    }
  }

  private fun updateIconTheme(iconTheme: String?) {
    (buttonPanes as? LinuxResizableCustomFrameButtons)?.updateIconTheme(iconTheme)
  }

  private fun createToolbarPlaceholder(): JPanel {
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.border = JBUI.Borders.empty(0, JBUI.scale(4))
    return panel
  }

  private fun createMenuBarContainer(): JPanel {
    val panel = JPanel(GridLayout())
    panel.isOpaque = false
    RowsGridBuilder(panel).defaultVerticalAlign(VerticalAlign.FILL)
      .row(resizable = true)
      .cell(component = ideMenuBar, resizableColumn = true)
      .cell(menuBarHeaderTitle, resizableColumn = true)
      .columnsGaps(listOf(UnscaledGapsX.EMPTY, UnscaledGapsX(44)))
    return panel
  }

  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      updateCustomTitleBar()
    }
  }

  private val mode: ShowMode
    get() {
      val toolbarInHeader = CustomWindowHeaderUtil.isToolbarInHeader(UISettings.getInstance(), isFullScreen())
      return if (toolbarInHeader) ShowMode.TOOLBAR else ShowMode.MENU
    }

  private fun wrap(comp: JComponent): NonOpaquePanel {
    return object : NonOpaquePanel(comp) {
      override fun getPreferredSize() = comp.preferredSize
      override fun getMinimumSize() = comp.preferredSize
    }
  }

  override fun scheduleUpdateToolbar() {
    check(updateRequests.tryEmit(Unit))
  }

  override fun paintComponent(g: Graphics) {
    if (mode == ShowMode.MENU && menuBarHeaderTitle.isVisible ||
        toolbarHeaderTitle.parent != null ||
        !ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g as Graphics2D)) {
      // isOpaque is false to paint colorful toolbar gradient, so, we have to draw background on our own
      g.color = background
      g.fillRect(0, 0, width, height)
    }
  }

  private suspend fun doUpdateToolbar(compactHeader: Boolean) {
    val resetToolbar = compactHeader != isCompactHeader || toolbar == null

    if (!resetToolbar) {
      withContext(Dispatchers.EDT) {
        toolbarPlaceholder.revalidate()
        toolbarPlaceholder.repaint()
      }
      return
    }

    val newToolbar = withContext(Dispatchers.EDT) {
      toolbar?.removeComponentListener(contentResizeListener)
      toolbarPlaceholder.removeAll()
      MainToolbar(coroutineScope = coroutineScope.childScope(), frame = frame, isFullScreen = isFullScreen)
    }
    newToolbar.init(customTitleBar)
    withContext(Dispatchers.EDT) {
      newToolbar.addComponentListener(contentResizeListener)
      this@ToolbarFrameHeader.toolbar = newToolbar
      toolbarHeaderTitle.updateBorders(0)
      if (compactHeader) {
        toolbarPlaceholder.add(toolbarHeaderTitle, BorderLayout.CENTER)
      }
      else {
        toolbarPlaceholder.add(newToolbar, BorderLayout.CENTER)
      }

      toolbarPlaceholder.revalidate()
      toolbarPlaceholder.repaint()
    }
  }

  override fun installListeners() {
    super.installListeners()
    mainMenuButton.rootPane = frame.rootPane
    ideMenuBar.addComponentListener(contentResizeListener)
    ideMenuHelper.installListeners()
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    ideMenuBar.removeComponentListener(contentResizeListener)
    toolbar?.removeComponentListener(contentResizeListener)
    ideMenuHelper.uninstallListeners()
  }

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    expandableMenu.ideMenu.updateMenuActions(forceRebuild)
  }

  override fun getComponent(): JComponent = this

  override fun uiSettingsChanged(uiSettings: UISettings) {
    scheduleUpdateToolbar()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) {
      scheduleUpdateToolbar()
      updateMenuBar()
      ideMenuHelper.updateUI()
    }
  }

  private fun updateMenuBar() {
    if (hideNativeLinuxTitle(UISettings.shadowInstance)) {
      ideMenuBar.border = null
    }
  }

  override fun getHeaderBackground(active: Boolean) = CustomFrameDecorations.mainToolbarBackground(active)

  override fun updateActive() {
    super.updateActive()

    expandableMenu.updateColor()
  }

  private fun createHeaderContent(): JPanel {
    val menuPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(menuBarContainer, gb.next().fillCellVertically().weighty(1.0))
      add(createDraggableWindowArea(), gb.next().weightx(1.0).fillCell())
    }
    val toolbarPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(mainMenuButton.button, gb.next())
      add(toolbarPlaceholder, gb.next().weightx(1.0).fillCell())
    }

    val result = NonOpaquePanel(CardLayout()).apply {
      background = null
      add(ShowMode.MENU.name, menuPnl)
      add(ShowMode.TOOLBAR.name, toolbarPnl)
    }

    return result
  }

  private fun updateHeaderContentBorder(iconRightPosition: Boolean) {
    headerContent.border = JBUI.Borders.emptyLeft(JBUI.scale(if (iconRightPosition) 16 else 4))
  }

  private fun updateLayout() {
    (headerContent.layout as CardLayout).show(headerContent, mode.name)
  }

  private fun createDraggableWindowArea(): JComponent {
    val result = JLabel()
    if (hideNativeLinuxTitle(UISettings.shadowInstance)) {
      WindowMoveListener(this).apply {
        setLeftMouseButtonOnly(true)
        installTo(result)
        installTo(this@ToolbarFrameHeader)
      }
    }
    return result
  }
}
