// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.repaintWhenProjectGradientOffsetChanged
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.WindowButtonsConfiguration
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxIconThemeConfiguration
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxResizableCustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isCompactHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.HEADER_HEIGHT_DFM
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath.SimpleCustomDecorationPathComponent
import com.intellij.openapi.wm.impl.headertoolbar.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.*
import java.awt.GridBagConstraints.WEST
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel

internal class ToolbarFrameHeader(
  private val coroutineScope: CoroutineScope,
  frame: JFrame,
  private val ideMenuBar: IdeJMenuBar,
  private val isAlwaysCompact: Boolean,
  private val isFullScreen: () -> Boolean,
) : FrameHeader(frame), UISettingsListener, ToolbarHolder, MainFrameCustomHeader {
  private val ideMenuHelper = IdeMenuHelper(menu = ideMenuBar, coroutineScope = coroutineScope)
  private val menuBarHeaderTitle = SimpleCustomDecorationPathComponent(frame = frame, isGrey = {true}).apply {
    isOpaque = false
  }
  private val mainMenuWithButton = MainMenuWithButton(coroutineScope, frame)
  private val menuBarContainer = createMenuBarContainer()
  private val mainMenuButtonComponent = mainMenuWithButton.mainMenuButton.button
  private var toolbar: MainToolbar? = null
  private val toolbarPlaceholder = createToolbarPlaceholder()
  private val headerContent = createHeaderContent()
  private val expandableMenu = ExpandableMenu(headerContent = headerContent, coroutineScope = coroutineScope.childScope("ExpandableMenu"), frame) { !isCompactHeader }
  private val toolbarHeaderTitle = SimpleCustomDecorationPathComponent(frame = frame, isGrey = { mode != ShowMode.TOOLBAR }).apply {
    isOpaque = false
  }

  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var currentContentState: WindowButtonsConfiguration.State? = null

  @Volatile
  private var isCompactHeader: Boolean

  private val resizeListener = initListenerToResizeMenu()
  private val widthCalculationListener = object : ToolbarWidthCalculationListener {
    override fun onToolbarCompressed(event: ToolbarWidthCalculationEvent) {
      mainMenuWithButton.recalculateWidth(event.toolbar)
    }
  }

  init {
    // color full toolbar
    isOpaque = false
    isCompactHeader = isAlwaysCompact || isCompactHeader()

    mainMenuWithButton.mainMenuButton.expandableMenu = expandableMenu
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

    this.addComponentListener(resizeListener)

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      updateRequests.emit(Unit)
      updateRequests.collect {
        withContext(ModalityState.any().asContextElement()) {
          withContext(Dispatchers.EDT) {
            updateLayout()
          }

          val compactHeader = isAlwaysCompact || isCompactHeader { computeMainActionGroups() }

          when (mode) {
            ShowMode.TOOLBAR, ShowMode.TOOLBAR_WITH_MENU -> {
              withContext(Dispatchers.EDT) {
                mainMenuWithButton.recalculateWidth(toolbar)
                mainMenuButtonComponent.revalidate()
                mainMenuButtonComponent.repaint()
              }
              doUpdateToolbar(compactHeader)
            }
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
            mainMenuButtonComponent.setMinimumButtonSize(size)
            if (mode == ShowMode.MENU) {
              menuBarHeaderTitle.isVisible = isCompactHeader
            }
            mainMenuWithButton.recalculateWidth(toolbar)
            repaint()
          }
        }
      }
    }
    repaintWhenProjectGradientOffsetChanged(this)
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

  private val mode: ShowMode
    get() = ShowMode.getCurrent()

  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      updateCustomTitleBar()
    }
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

  override fun isColorfulToolbar(): Boolean = !isCompactHeader

  override fun paintComponent(g: Graphics) {
    if (mode == ShowMode.MENU && menuBarHeaderTitle.isVisible ||
        toolbarHeaderTitle.parent != null || isCompactHeader ||
        !ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g as Graphics2D)) {
      // isOpaque is false to paint colorful toolbar gradient, so, we have to draw background on our own
      g.color = background
      g.fillRect(0, 0, width, height)
    }
  }

  private suspend fun doUpdateToolbar(compactHeader: Boolean) {
    val resetToolbar = compactHeader != isCompactHeader || (compactHeader && mode != ShowMode.MENU) || toolbar == null

    if (!resetToolbar) {
      withContext(Dispatchers.EDT) {
        toolbarPlaceholder.revalidate()
        toolbarPlaceholder.repaint()
      }
      return
    }

    val newToolbar = withContext(Dispatchers.EDT) {
      toolbar?.removeComponentListener(contentResizeListener)
      toolbar?.removeComponentListener(resizeListener)
      toolbar?.removeWidthCalculationListener(widthCalculationListener)
      toolbarPlaceholder.removeAll()
      MainToolbar(coroutineScope = coroutineScope.childScope("MainToolbar"), frame = frame, isFullScreen = isFullScreen)
    }
    newToolbar.init(customTitleBar)

    withContext(Dispatchers.EDT) {
      newToolbar.addComponentListener(resizeListener)
      newToolbar.addComponentListener(contentResizeListener)
      newToolbar.addWidthCalculationListener(widthCalculationListener)
      this@ToolbarFrameHeader.toolbar = newToolbar
      toolbarHeaderTitle.updateBorders(0)
      if (compactHeader) {
        toolbarHeaderTitle.updateLabelForeground()
        toolbarPlaceholder.add(toolbarHeaderTitle, if (mode == ShowMode.TOOLBAR_WITH_MENU) BorderLayout.WEST else BorderLayout.CENTER)
      }
      else {
        toolbarPlaceholder.add(newToolbar, BorderLayout.CENTER)
      }

      mainMenuWithButton.recalculateWidth(newToolbar)
      newToolbar.revalidate()
      newToolbar.repaint()
      toolbarPlaceholder.revalidate()
      toolbarPlaceholder.repaint()
      this@ToolbarFrameHeader.revalidate()
      this@ToolbarFrameHeader.repaint()
    }
  }

  override fun installListeners() {
    super.installListeners()
    mainMenuWithButton.mainMenuButton.rootPane = frame.rootPane
    ideMenuBar.addComponentListener(contentResizeListener)
    this.addComponentListener(resizeListener)
    ideMenuHelper.installListeners()
    toolbar?.addWidthCalculationListener(widthCalculationListener)
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    ideMenuBar.removeComponentListener(contentResizeListener)
    toolbar?.removeComponentListener(contentResizeListener)
    toolbar?.removeComponentListener(resizeListener)
    toolbar?.removeWidthCalculationListener(widthCalculationListener)
    this.removeComponentListener(resizeListener)
    ideMenuHelper.uninstallListeners()
  }

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    expandableMenu.ideMenu.updateMenuActions(forceRebuild)
  }

  /**
   *  Used exclusively for `ShowMode.TOOLBAR_WITH_MENU`.
   * This method initializes and returns a `ComponentListener` to dynamically manage the toolbar's menu items
   *  during resize events.
   *  The listener adjusts the visibility and layout of menu items based on the available space,
   *  ensuring an optimal fit within the toolbar while maintaining usability.
   *
   * The toolbar's size is prioritized over the menu's size, preventing the toolbar from being compressed when resizing operations occur.
   */
  private fun initListenerToResizeMenu(): ComponentListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      mainMenuWithButton.recalculateWidth(toolbar)
      this@ToolbarFrameHeader.revalidate()
      this@ToolbarFrameHeader.repaint()
    }
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
      mainMenuWithButton.toolbarMainMenu.border = null
    }
  }

  override fun getHeaderBackground(active: Boolean): Color {
    val color = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(isActive)
    return InternalUICustomization.getInstance()?.frameHeaderBackgroundConverter(color) ?: color
  }

  override fun getComponentGraphics(graphics: Graphics?): Graphics? {
    val componentGraphics = super.getComponentGraphics(graphics)
    return InternalUICustomization.getInstance()?.transformGraphics(this, componentGraphics) ?: componentGraphics
  }

  override fun updateActive() {
    super.updateActive()

    expandableMenu.updateColor()
  }

  private fun createHeaderContent(): JPanel {
    val menuPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(menuBarContainer, gb.next().fillCellVertically().weighty(1.0))
      add(createDraggableWindowArea(), gb.next().weightx(1.0).fillCell())
      isVisible = ShowMode.getCurrent() == ShowMode.MENU
    }
    val toolbarPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(mainMenuWithButton, gb.next().fillCellVertically().weighty(1.0))
      add(toolbarPlaceholder, gb.next().weightx(1.0).fillCell())
      isVisible = ShowMode.getCurrent() != ShowMode.MENU
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
    (headerContent.layout as CardLayout).show(headerContent, if (mode == ShowMode.TOOLBAR_WITH_MENU) ShowMode.TOOLBAR.name else mode.name)
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
