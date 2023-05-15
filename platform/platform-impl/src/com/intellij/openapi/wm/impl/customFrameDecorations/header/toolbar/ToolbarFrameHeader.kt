// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.AdjustableSizeCardLayout
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.isToolbarInHeader
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGapsX
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import com.jetbrains.CustomWindowDecoration.MENU_BAR
import java.awt.*
import java.awt.GridBagConstraints.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

private enum class ShowMode {
  MENU, TOOLBAR
}

internal class ToolbarFrameHeader(frame: JFrame, private val root: IdeRootPane) : FrameHeader(frame), UISettingsListener, ToolbarHolder, MainFrameCustomHeader {
  private val myMenuBar = IdeMenuBar.createMenuBar()
  private val menuBarHeaderTitle = SimpleCustomDecorationPath(frame, true).apply {
    isOpaque = false
  }
  private val menuBarContainer = createMenuBarContainer()
  private val mainMenuButton = MainMenuButton()
  private var toolbar : MainToolbar? = null
  private val myToolbarPlaceholder = createToolbarPlaceholder()
  private val myHeaderContent = createHeaderContent()
  private val expandableMenu = ExpandableMenu(myHeaderContent)
  private val toolbarHeaderTitle = SimpleCustomDecorationPath(frame).apply {
    isOpaque = false
  }
  private val customizer get() = ProjectWindowCustomizerService.getInstance()
  private var customizerServiceDisposable: Disposable? = null

  init {
    updateMenuBar()
  }

  private fun createToolbarPlaceholder(): JPanel {
    val panel = JPanel(CardLayout())
    panel.isOpaque = false
    panel.layout = AdjustableSizeCardLayout()
    panel.border = JBUI.Borders.empty(0, JBUI.scale(4))
    return panel
  }

  private fun createMenuBarContainer(): JPanel {
    val panel = JPanel(GridLayout())
    panel.isOpaque = false
    RowsGridBuilder(panel).defaultVerticalAlign(VerticalAlign.FILL)
      .row(resizable = true)
      .cell(component = myMenuBar, resizableColumn = true)
      .cell(menuBarHeaderTitle, resizableColumn = true)
      .columnsGaps(listOf(UnscaledGapsX.EMPTY, UnscaledGapsX(44)))
    return panel
  }

  private val contentResizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      updateCustomDecorationHitTestSpots()
    }
  }

  private val mode: ShowMode
    get() = if (isToolbarInHeader(UISettings.shadowInstance)) ShowMode.TOOLBAR else ShowMode.MENU
  private val isCompact: Boolean get() = (root as? IdeRootPane)?.isCompactHeader == true

  private fun toolbarCardName(isCompact: Boolean = this.isCompact): String =
    if (isCompact) "PATH" else "TOOLBAR"

  init {
    mainMenuButton.expandableMenu = expandableMenu
    layout = GridBagLayout()
    val gb = GridBag().anchor(WEST)

    productIcon.border = JBUI.Borders.empty(V, 0, V, 0)
    add(productIcon, gb.nextLine().next().anchor(WEST).insetLeft(H))
    add(myHeaderContent, gb.next().fillCell().anchor(CENTER).weightx(1.0).weighty(1.0))
    val buttonsView = wrap(buttonPanes.getView())
    add(buttonsView, gb.next().anchor(EAST))

    setCustomFrameTopBorder({ false }, {true})

    customizer.addListener(this, true) {
      isOpaque = !it
      revalidate()
    }

    updateToolbar()
  }

  private fun wrap(comp: JComponent) = object : NonOpaquePanel(comp) {
    override fun getPreferredSize(): Dimension = comp.preferredSize
    override fun getMinimumSize(): Dimension = comp.preferredSize
  }

  override fun initToolbar(toolbarActionGroups: List<Pair<ActionGroup, String>>) {
    doUpdateToolbar(toolbarActionGroups)
  }

  override fun updateToolbar() {
    updateLayout()

    when (mode) {
      ShowMode.TOOLBAR -> doUpdateToolbar(MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()))
      ShowMode.MENU -> removeToolbar()
    }

    updateToolbarAppearanceFromMode()
  }

  override fun paint(g: Graphics?) {
    customizer.paint(frame, this, g)
    super.paint(g)
  }

  @RequiresEdt
  private fun doUpdateToolbar(toolbarActionGroups: List<Pair<ActionGroup, String>>) {
    removeToolbar()

    val toolbar = MainToolbar()
    toolbar.layoutCallBack = { updateCustomDecorationHitTestSpots() }
    toolbar.init(toolbarActionGroups)
    toolbar.isOpaque = false
    toolbar.addComponentListener(contentResizeListener)
    this.toolbar = toolbar
    myToolbarPlaceholder.add(toolbar, toolbarCardName(false))

    toolbarHeaderTitle.updateBorders(0)
    myToolbarPlaceholder.add(toolbarHeaderTitle, toolbarCardName(true))

    (myToolbarPlaceholder.layout as CardLayout).show(myToolbarPlaceholder, toolbarCardName())
    myToolbarPlaceholder.revalidate()
  }

  private fun removeToolbar() {
    toolbar?.removeComponentListener(contentResizeListener)
    myToolbarPlaceholder.removeAll()
    myToolbarPlaceholder.revalidate()
  }

  private fun updateMenuButtonMinimumSize() {
    mainMenuButton.button.setMinimumButtonSize(
      if (isCompact) Dimension(toolbarHeaderTitle.expectedHeight, toolbarHeaderTitle.expectedHeight)
      else ActionToolbar.experimentalToolbarMinimumButtonSize()
    )
  }

  private fun updateMenuBarAppearance() {
    menuBarHeaderTitle.isVisible = (isCompact && mode == ShowMode.MENU)
  }

  private fun updateTitleButtonsMode() {
    buttonPanes.isCompactMode = isCompact
  }

  override fun installListeners() {
    super.installListeners()
    mainMenuButton.rootPane = frame.rootPane
    myMenuBar.addComponentListener(contentResizeListener)
    customizerServiceDisposable = Disposer.newDisposable()
    ProjectWindowCustomizerService.getInstance().addListener(customizerServiceDisposable!!, true) {
      myMenuBar.updateMenuSelectionBackground()
    }
  }

  override fun uninstallListeners() {
    super.uninstallListeners()
    myMenuBar.removeComponentListener(contentResizeListener)
    toolbar?.removeComponentListener(contentResizeListener)
    customizerServiceDisposable?.let {
      Disposer.dispose(it)
      customizerServiceDisposable = null
    }
  }

  override fun updateMenuActions(forceRebuild: Boolean) {
    myMenuBar.updateMenuActions(forceRebuild)
    expandableMenu.ideMenu.updateMenuActions(forceRebuild)
  }

  override fun getComponent(): JComponent = this

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateToolbar()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) {
      updateToolbar()
      updateMenuBar()
    }
  }

  private fun updateMenuBar() {
    myMenuBar.updateMenuSelectionBackground()
    if (IdeRootPane.hideNativeLinuxTitle) {
      myMenuBar.border = null
      setMenuColor(myMenuBar, myHeaderContent.background)
    }
  }

  private fun updateToolbarAppearanceFromMode() {
    updateTitleButtonsMode()
    updateMenuButtonMinimumSize()
    if (mode == ShowMode.MENU) updateMenuBarAppearance()
  }

  override fun getHitTestSpots(): Sequence<Pair<RelativeRectangle, Int>> {
    return when (mode) {
      ShowMode.MENU -> {
        super.getHitTestSpots() + Pair(getElementRect(myMenuBar) { rect ->
          val state = frame.extendedState
          if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
            val topGap = (rect.height / 3).toFloat().roundToInt()
            rect.y += topGap
            rect.height -= topGap
          }
        }, MENU_BAR)
      }
      ShowMode.TOOLBAR -> {
        super.getHitTestSpots() +
        Pair(getElementRect(mainMenuButton.button), MENU_BAR) +
        if (isCompact) emptySequence()
        else (toolbar?.components?.asSequence()?.filter { it.isVisible }?.map { Pair(getElementRect(it), MENU_BAR) } ?: emptySequence())
      }
    }
  }

  override fun getHeaderBackground(active: Boolean) = CustomFrameDecorations.mainToolbarBackground(active)

  override fun updateActive() {
    super.updateActive()

    expandableMenu.updateColor()
    updateMenuBar()
  }

  private fun getElementRect(comp: Component, rectProcessor: ((Rectangle) -> Unit)? = null): RelativeRectangle {
    val rect = Rectangle(comp.size)
    rectProcessor?.invoke(rect)
    return RelativeRectangle(comp, rect)
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
      add(myToolbarPlaceholder, gb.next().weightx(1.0).fillCell())
    }

    val result = NonOpaquePanel(CardLayout()).apply {
      border = JBUI.Borders.emptyLeft(JBUI.scale(16))
      background = null
      add(ShowMode.MENU.name, menuPnl)
      add(ShowMode.TOOLBAR.name, toolbarPnl)
    }

    return result
  }

  private fun updateLayout() {
    val layout = myHeaderContent.layout as CardLayout
    layout.show(myHeaderContent, mode.name)
  }

  private fun createDraggableWindowArea(): JComponent {
    val result = JLabel()
    if (IdeRootPane.hideNativeLinuxTitle) {
      val windowMoveListener = WindowMoveListener(this)
      windowMoveListener.installTo(result)
      windowMoveListener.installTo(this)
    }
    return result
  }
}

internal fun setMenuColor(menu: IdeMenuBar, color: Color?) {
  if (IdeRootPane.hideNativeLinuxTitle) {
    for (i in 0..menu.menuCount - 1) {
      menu.getMenu(i).background = color
    }
  }
}