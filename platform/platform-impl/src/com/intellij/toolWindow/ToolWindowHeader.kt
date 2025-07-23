// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToolwindowFusEventFields
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.FusAwareAction
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.impl.DockToolWindowAction
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.content.SingleContentLayout
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.*
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.popup.PopupState
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.function.Supplier
import javax.swing.*
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

@ApiStatus.Internal
abstract class ToolWindowHeader internal constructor(
  private val toolWindow: ToolWindowImpl,
  private val contentUi: ToolWindowContentUi,
  private val gearProducer: Supplier<ActionGroup>
) : BorderLayoutPanel(), PropertyChangeListener {

  @ApiStatus.Internal
  companion object {
    internal fun getUnscaledHeight() = if (ExperimentalUI.isNewUI()) {
      JBUI.CurrentTheme.ToolWindow.headerHeight()
    }
    else {
      SingleHeightTabs.UNSCALED_PREF_HEIGHT
    }
  }

  private val actionGroup = DefaultActionGroup()
  private val toolbar: ActionToolbar
  private val westPanel = WestPanel()
  private val popupMenuListener = object : PopupMenuListener {
    override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = setPopupShowing(true)
    override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = setPopupShowing(false)
    override fun popupMenuCanceled(event: PopupMenuEvent) = setPopupShowing(false)
  }

  var isPopupShowing: Boolean = false
    private set

  var sideComponent: JComponent? = null
    set(value) {
      val old = field
      if (old !== value) {
        westPanel.setComponents(contentUi.tabComponent, value)
        field = value
        westPanel.revalidateAndRepaint()
      }
    }

  private fun setPopupShowing(showing: Boolean) {
    if (isPopupShowing != showing) {
      isPopupShowing = showing
      toolWindow.decorator?.updateActiveAndHoverState()
    }
  }

  init {
    @Suppress("LeakingThis")
    AccessibleContextUtil.setName(this, IdeBundle.message("toolwindow.header.accessible.name"))
    westPanel.setComponents(contentUi.tabComponent, null)
    @Suppress("LeakingThis")
    add(westPanel.component)
    ToolWindowContentUi.initMouseListeners(westPanel.component, contentUi, true)
    val commonActionsGroup = DefaultActionGroup(DockToolWindowAction(), ShowOptionsAction(), HideAction())
    toolbar = object : ActionToolbarImpl(
      ActionPlaces.TOOLWINDOW_TITLE,
      object : ActionGroup(), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun getChildren(e: AnActionEvent?): Array<AnAction> {
          if (e == null) {
            return EMPTY_ARRAY
          }
          val nearestDecorator = InternalDecoratorImpl.findNearestDecorator(e.getData(PlatformDataKeys.CONTEXT_COMPONENT))
          val hideCommonActions = if (nearestDecorator is Component) {
            ClientProperty.get(nearestDecorator as Component?, InternalDecoratorImpl.HIDE_COMMON_TOOLWINDOW_BUTTONS)
          }
          else {
            null
          }

          val extraActions = mutableListOf<AnAction>(actionGroup)
          if (ExperimentalUI.isNewUI()) {
            val singleContentLayout = contentUi.currentLayout as? SingleContentLayout
            if (singleContentLayout != null) {
              val contentActions = singleContentLayout.getSupplier()?.getContentActions()
              if (!contentActions.isNullOrEmpty()) {
                extraActions.addAll(0, contentActions)
                extraActions.add(0, Separator.create())
                extraActions.add(0, singleContentLayout.closeCurrentContentAction)
              }
            }
          }

          val tabListAction = e.actionManager.getAction("TabList")
          if (hideCommonActions == true) {
            return arrayOf(tabListAction)
          }
          return arrayOf(tabListAction, *extraActions.toTypedArray(), commonActionsGroup)
        }
      },
      true
    ) {
      override fun getDataContext(): DataContext {
        val content = contentUi.contentManager.selectedContent
        val target = content?.preferredFocusableComponent ?: content?.component ?: this
        if (targetComponent != target) {
          targetComponent = target
        }
        return super.getDataContext()
      }

      override fun removeNotify() {
        super.removeNotify()
        targetComponent = this
      }
    }

    toolbar.targetComponent = toolbar.component
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    toolbar.isReservePlaceAutoPopupIcon = false
    val component = toolbar.component
    component.border = JBUI.Borders.empty(2, 0)
    if (toolWindow.toolWindowManager.isNewUi) {
      component.border = JBUI.Borders.empty(JBUI.CurrentTheme.ToolWindow.headerToolbarLeftRightInsets())
    }
    component.isOpaque = false

    val toolbarPanel = object : JPanel(HorizontalLayout(0, SwingConstants.CENTER)) {
      override fun getPreferredSize(): Dimension {
        return if (toolWindow.toolWindowManager.isNewUi
                   && toolWindow.anchor != ToolWindowAnchor.LEFT
                   && toolWindow.anchor != ToolWindowAnchor.RIGHT) {
          toolbar.component.preferredSize
        }
        else super.getPreferredSize()
      }
    }
    toolbarPanel.isOpaque = false
    toolbarPanel.add(component)

    @Suppress("LeakingThis")
    add(toolbarPanel, BorderLayout.EAST)
    westPanel.component.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          toolWindow.fireActivated(ToolWindowEventSource.ToolWindowHeader)
        }
      }
    )

    @Suppress("LeakingThis")
    addMouseListener(
      object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          if (e.isPopupTrigger) {
            return
          }

          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            if (e.isAltDown) {
              toolWindow.fireHidden(ToolWindowEventSource.ToolWindowHeaderAltClick)
            }
            else {
              toolWindow.fireHiddenSide(ToolWindowEventSource.ToolWindowHeader)
            }
          }
          else {
            toolWindow.fireActivated(ToolWindowEventSource.ToolWindowHeader)
            // Move focus to the context component.
            val decorator = InternalDecoratorImpl.findNearestDecorator(this@ToolWindowHeader)
            decorator?.requestContentFocus()
          }
        }
      }
    )

    isOpaque = true
    if (toolWindow.toolWindowManager.isNewUi) {
      border = JBUI.Borders.empty()
    }

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val manager = toolWindow.toolWindowManager
        manager.setMaximized(this@ToolWindowHeader.toolWindow, !manager.isMaximized(this@ToolWindowHeader.toolWindow))
        return true
      }
    }.installOn(westPanel.component)
    westPanel.component.addMouseListener(
      object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          val runnable = Runnable { dispatchEvent(SwingUtilities.convertMouseEvent(e.component, e, this@ToolWindowHeader)) }
          SwingUtilities.invokeLater(runnable)
        }
      }
    )
  }

  private fun manageWestPanelTabComponentAndToolbar(init: Boolean) {
    if (init) {
      westPanel.growFirst = ToolWindowContentUi.isTabsReorderingAllowed(toolWindow)
      westPanel.setComponents(contentUi.tabComponent, sideComponent)
      contentUi.connectTabToolbar()
    }
    else { // remove to avoid extra events, toolbars update on addNotify!
      westPanel.clear()
      contentUi.disconnectTabToolbar()
    }
  }

  override fun propertyChange(evt: PropertyChangeEvent?) {
    manageWestPanelTabComponentAndToolbar(true)
  }

  override fun addNotify() {
    super.addNotify()
    toolWindow.component.addPropertyChangeListener(ToolWindowContentUi.ALLOW_TABS_REORDERING.toString(), this)
    manageWestPanelTabComponentAndToolbar(true)
  }

  override fun removeNotify() {
    toolWindow.component.removePropertyChangeListener(ToolWindowContentUi.ALLOW_TABS_REORDERING.toString(), this)
    super.removeNotify()
    manageWestPanelTabComponentAndToolbar(false)
  }

  fun getToolbar(): ActionToolbar = toolbar

  fun getToolbarActions(): DefaultActionGroup = actionGroup

  fun setAdditionalTitleActions(actions: List<AnAction>) {
    actionGroup.removeAll()
    actionGroup.addAll(actions)
    if (actions.isNotEmpty() && !ExperimentalUI.isNewUI()) {
      actionGroup.addSeparator()
    }
    toolbar.updateActionsAsync()
  }

  override fun paintComponent(g: Graphics) {
    if (toolWindow.isDisposed) {
      return
    }

    val nearestDecorator = InternalDecoratorImpl.findNearestDecorator(this@ToolWindowHeader)
    val isNewUi = toolWindow.toolWindowManager.isNewUi
    val drawTopLine = InternalDecoratorImpl.headerNeedsTopBorder(this)
    var drawBottomLine = true

    if (isNewUi) {
      val scrolled = ClientProperty.isTrue(nearestDecorator, SimpleToolWindowPanel.SCROLLED_STATE)
      val contentCount = (nearestDecorator?.contentManager ?: toolWindow.contentManager).contentCount
      drawBottomLine = (toolWindow.anchor == ToolWindowAnchor.BOTTOM
                        || (toolWindow.windowInfo.contentUiType == ToolWindowContentUiType.TABBED && contentCount > 1)
                        || toolWindow.hasTopToolbar()
                        || scrolled)
    }

    val active = !isNewUi && isActive
    UIUtil.drawHeader(g, 0, width, height, active, true, drawTopLine, drawBottomLine)
  }

  @Suppress("UseJBColor")
  override fun paintChildren(g: Graphics) {
    val graphics = g.create() as Graphics2D
    setupAntialiasing(graphics)
    super.paintChildren(graphics)
    val r = bounds
    if (!isActive && !StartupUiUtil.isDarkTheme) {
      graphics.color = Color(255, 255, 255, 30)
      graphics.fill(r)
    }
    graphics.dispose()
  }

  protected abstract val isActive: Boolean

  protected abstract fun hideToolWindow()

  override fun getPreferredSize(): Dimension {
    val insets = insets
    val top = if (InternalDecoratorImpl.headerNeedsTopBorder(this)) 1 else 0
    val height = JBUI.scale(getUnscaledHeight()) + top - insets.top - insets.bottom
    val size = super.getPreferredSize()
    return Dimension(size.width, height)
  }

  inner class ShowOptionsAction : DumbAwareAction(), FusAwareAction {
    private val myPopupState = PopupState.forPopupMenu()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      if (myPopupState.isRecentlyHidden) return // do not show new popup
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, gearProducer.get())
      popupMenu.setTargetComponent(e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JComponent ?: this@ToolWindowHeader)
      var x = 0
      var y = 0
      val inputEvent = e.inputEvent
      if (inputEvent is MouseEvent) {
        x = inputEvent.x
        y = inputEvent.y
      }
      myPopupState.prepareToShow(popupMenu.component)
      popupMenu.component.addPopupMenuListener(popupMenuListener)
      popupMenu.component.show(inputEvent!!.component, x, y)
    }

    override fun getAdditionalUsageData(event: AnActionEvent): List<EventPair<*>> {
      return listOf(ToolwindowFusEventFields.TOOLWINDOW with toolWindow.id)
    }

    init {
      copyFrom(gearProducer.get())
    }
  }

  private inner class HideAction : DumbAwareAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      hideToolWindow()
    }

    init {
      ActionUtil.copyFrom(this, InternalDecoratorImpl.HIDE_ACTIVE_WINDOW_ACTION_ID)
      templatePresentation.icon = AllIcons.General.HideToolWindow
      templatePresentation.setText(UIBundle.messagePointer("tool.window.hide.action.name"))
    }
  }
}

private class WestPanel {
  val component = JPanel().apply {
    isOpaque = false
    MouseDragHelper.setComponentDraggable(this, true)
  }

  var growFirst = false

  fun setComponents(first: Component, second: Component?) {
    clear()
    val layout = GroupLayout(component)
    val hg: GroupLayout.SequentialGroup = layout.createSequentialGroup()
    val vg: GroupLayout.ParallelGroup = layout.createParallelGroup(GroupLayout.Alignment.CENTER)
    layout.setHorizontalGroup(hg)
    layout.setVerticalGroup(vg)
    component.layout = layout

    hg.addComponent(first, DEFAULT_SIZE, DEFAULT_SIZE, if (growFirst) INFINITE_SIZE else PREFERRED_SIZE)
    vg.addComponent(first, DEFAULT_SIZE, DEFAULT_SIZE, INFINITE_SIZE)
    if (second != null) {
      hg.addComponent(second, DEFAULT_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
      vg.addGroup(
        layout.createSequentialGroup()
          .addGap(JBUI.scale(1))
          .addComponent(second, DEFAULT_SIZE, DEFAULT_SIZE, INFINITE_SIZE)
          .addGap(JBUI.scale(1))
      )
    }
  }

  fun clear() {
    component.removeAll()
  }

  fun revalidateAndRepaint() {
    component.revalidate()
    component.repaint()
  }
}

private const val INFINITE_SIZE: Int = Short.MAX_VALUE.toInt()
