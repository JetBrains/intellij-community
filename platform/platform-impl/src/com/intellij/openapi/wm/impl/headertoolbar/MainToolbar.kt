// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.ActionUrl
import com.intellij.ide.ui.customization.CustomActionsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.ui.laf.darcula.ui.MainToolbarComboBoxButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ExpandableMenu
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.ui.*
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.touchbar.TouchbarSupport
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.*
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

private const val MAIN_TOOLBAR_ID = IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI

internal class MainToolbar(private val coroutineScope: CoroutineScope, frame: JFrame) : JPanel(HorizontalLayout(10)) {
  private val disposable = Disposer.newDisposable()
  private val mainMenuButton: MainMenuButton?
  private val expandableMenu: ExpandableMenu?

  var layoutCallBack : LayoutCallBack? = null

  init {
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)
    isOpaque = true
    if (IdeRootPane.isMenuButtonInToolbar) {
      mainMenuButton = MainMenuButton()
      expandableMenu = ExpandableMenu(headerContent = this, coroutineScope = coroutineScope, frame)
      mainMenuButton.expandableMenu = expandableMenu
    }
    else {
      mainMenuButton = null
      expandableMenu = null
    }
    ClientProperty.put(this, IdeBackgroundUtil.NO_BACKGROUND, true)
  }

  companion object {
    internal suspend fun computeActionGroups(): List<Pair<ActionGroup, String>> {
      val app = ApplicationManager.getApplication()
      app.serviceAsync<ActionManager>()
      val customActionSchema = app.serviceAsync<CustomActionsSchema>()
      return computeActionGroups(customActionSchema)
    }

    fun computeActionGroups(customActionSchema: CustomActionsSchema): List<Pair<ActionGroup, String>> {
      return sequenceOf(
        GroupInfo("MainToolbarLeft", ActionsTreeUtil.getMainToolbarLeft(), HorizontalLayout.LEFT),
        GroupInfo("MainToolbarCenter", ActionsTreeUtil.getMainToolbarCenter(), HorizontalLayout.CENTER),
        GroupInfo("MainToolbarRight", ActionsTreeUtil.getMainToolbarRight(), HorizontalLayout.RIGHT)
      )
        .mapNotNull { info ->
          customActionSchema.getCorrectedAction(info.id, info.name)?.let {
            it to info.align
          }
        }
        .toList()
    }
  }

  override fun getComponentGraphics(g: Graphics): Graphics = super.getComponentGraphics(IdeBackgroundUtil.getOriginalGraphics(g))

  // Separate init because first, as part of IdeRootPane creation, we add bare component to allocate space and then,
  // as part of EDT task scheduled in a start-up activity, do fill it. That's to avoid flickering due to resizing.
  @RequiresEdt
  fun init(actionGroups: List<Pair<ActionGroup, String>>, customTitleBar: WindowDecorations.CustomTitleBar? = null) {
    removeAll()

    mainMenuButton?.let {
      addWidget(it.button, HorizontalLayout.LEFT)
    }

    val schema = CustomActionsSchema.getInstance()
    val customizationGroup = schema.getCorrectedAction(MAIN_TOOLBAR_ID) as? ActionGroup

    for ((actionGroup, position) in actionGroups) {
      addWidget(widget = createActionBar(actionGroup, customizationGroup), position = position)
    }

    customizationGroup
      ?.let { CustomizationUtil.createToolbarCustomizationHandler(it, MAIN_TOOLBAR_ID, this, ActionPlaces.MAIN_TOOLBAR) }
      ?.let { installClickListener(it, customTitleBar) }

    migratePreviousCustomizations(schema)
  }

  /*
   * this is temporary solutions for migration customizations from 2023.1 version
   * todo please remove it when users are not migrate any more from 2023.1
   */
  private fun migratePreviousCustomizations(schema: CustomActionsSchema) {
    val mainToolbarName = schema.getDisplayName(MAIN_TOOLBAR_ID) ?: return
    val mainToolbarPath = listOf("root", mainToolbarName)
    val backup = CustomActionsSchema(null)
    backup.copyFrom(schema)
    val url = ActionUrl().apply { groupPath = ArrayList(mainToolbarPath) }
    if (!schema.getChildActions(url).isEmpty()) return

    val tmpSchema = CustomActionsSchema(null)
    tmpSchema.copyFrom(schema)

    val changed = migrateToolbar(tmpSchema, listOf("root", "Main Toolbar Left"), mainToolbarPath + "Left")
                  || migrateToolbar(tmpSchema, listOf("root", "Main Toolbar Center"), mainToolbarPath + "Center")
                  || migrateToolbar(tmpSchema, listOf("root", "Main Toolbar Right"), mainToolbarPath + "Right")

    if (changed) {
      schema.copyFrom(tmpSchema)
      schemaChanged()
    }
  }

  private fun migrateToolbar(schema: CustomActionsSchema, fromPath: List<String>, toPath: List<String>): Boolean {
    val parentURL = ActionUrl().apply { groupPath = ArrayList(fromPath) }
    val childActions = schema.getChildActions(parentURL)
    if (childActions.isEmpty()) return false

    val newUrls = childActions.map { ActionUrl(ArrayList(toPath), it.component, it.actionType, it.absolutePosition) }
    val actions = schema.getActions().toMutableList()
    actions.addAll(newUrls)
    actions.removeIf { url: ActionUrl -> fromPath == url.groupPath }
    schema.setActions(actions)
    return true
  }

  private fun schemaChanged() {
    CustomActionsSchema.getInstance().initActionIcons()
    CustomActionsSchema.setCustomizationSchemaForCurrentProjects()
    if (SystemInfo.isMac) {
      TouchbarSupport.reloadAllActions()
    }
    CustomActionsListener.fireSchemaChanged()
  }

  private fun installClickListener(popupHandler: PopupHandler, customTitleBar: WindowDecorations.CustomTitleBar?) {
    if (IdeRootPane.hideNativeLinuxTitle) {
      return
    }

    if (customTitleBar == null) {
      addMouseListener(popupHandler)
      return
    }

    val listener = object : HeaderClickTransparentListener(customTitleBar) {
      private fun handlePopup(e: MouseEvent) {
        if (e.isPopupTrigger) {
          popupHandler.invokePopup(e.component, e.x, e.y)
          e.consume()
        }
        else {
          hit()
        }
      }

      override fun mouseClicked(e: MouseEvent) = handlePopup(e)
      override fun mousePressed(e: MouseEvent) = handlePopup(e)
      override fun mouseReleased(e: MouseEvent) = handlePopup(e)
    }
    addMouseListener(listener)
    addMouseMotionListener(listener)
  }

  override fun addNotify() {
    super.addNotify()
    mainMenuButton?.rootPane = rootPane
  }

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(disposable)
    coroutineScope.cancel()
  }

  private fun addWidget(widget: JComponent, position: String) {
    add(position, widget)
    (widget as? Disposable)?.let { Disposer.register(disposable, it) }
  }

  private fun createActionBar(group: ActionGroup, customizationGroup: ActionGroup?): JComponent {
    val toolbar = MyActionToolbarImpl(group, layoutCallBack, customizationGroup, MAIN_TOOLBAR_ID)
    toolbar.setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))
    toolbar.setActionButtonBorder(2, 5)
    toolbar.setCustomButtonLook(HeaderToolbarButtonLook())

    toolbar.setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
    toolbar.targetComponent = null
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    val component = toolbar.component
    component.border = JBUI.Borders.empty()
    component.isOpaque = false
    return component
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) accessibleContext = AccessibleMainToolbar()
    accessibleContext.accessibleName =
      if (ExperimentalUI.isNewUI() && UISettings.getInstance().separateMainMenu) {
        UIBundle.message("main.toolbar.accessible.group.name")
      }
      else {
        ""
      }
    return accessibleContext
  }

  private inner class AccessibleMainToolbar : AccessibleJPanel() {
    override fun getAccessibleRole(): AccessibleRole = AccessibilityUtils.GROUPED_ELEMENTS
  }
}

typealias LayoutCallBack = () -> Unit

private class MyActionToolbarImpl(group: ActionGroup, val layoutCallBack: LayoutCallBack?, customizationGroup: ActionGroup?, customizationGroupID: String)
  : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, group, true, false, true, customizationGroup, customizationGroupID) {

  private val iconUpdater = HeaderIconUpdater()

  init {
    updateFont()
    ClientProperty.put(this, IdeBackgroundUtil.NO_BACKGROUND, true)
  }

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    super.calculateBounds(size2Fit, bounds)
    for (i in 0 until bounds.size) {
      val prevRect = if (i > 0) bounds[i - 1] else null
      val rect = bounds[i]
      fitRectangle(prevRect, rect, getComponent(i), size2Fit.height)
    }
  }

  override fun doLayout() {
    super.doLayout()
    layoutCallBack?.invoke()
  }

  private fun fitRectangle(prevRect: Rectangle?, currRect: Rectangle, cmp: Component, toolbarHeight: Int) {
    val minSize = ActionToolbar.experimentalToolbarMinimumButtonSize()
    if (!isSeparator(cmp)) {
      currRect.width = Integer.max(currRect.width, minSize.width)
    }
    currRect.height = Integer.max(currRect.height, minSize.height)
    if (prevRect != null && prevRect.maxX > currRect.minX) {
      currRect.x = prevRect.maxX.toInt()
    }
    currRect.y = (toolbarHeight - currRect.height) / 2
  }

  override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    val component = super.createCustomComponent(action, presentation)

    if (component.foreground != null) {
      component.foreground = JBColor.namedColor("MainToolbar.foreground", component.foreground)
    }

    adjustIcons(presentation)

    if (action is ComboBoxAction) {
      findComboButton(component)?.apply {
        margin = JBInsets.emptyInsets()
        setUI(MainToolbarComboBoxButtonUI())
        addPropertyChangeListener("UI") { event -> if (event.newValue !is MainToolbarComboBoxButtonUI) setUI(MainToolbarComboBoxButtonUI())}
      }
    }
    return component
  }

  private fun adjustIcons(presentation: Presentation) {
    iconUpdater.registerFor(presentation, "icon", { it.icon }, { pst, icn -> pst.icon = icn})
    iconUpdater.registerFor(presentation, "selectedIcon", { it.selectedIcon }, { pst, icn -> pst.selectedIcon = icn})
    iconUpdater.registerFor(presentation, "hoveredIcon", { it.hoveredIcon }, { pst, icn -> pst.hoveredIcon = icn})
    iconUpdater.registerFor(presentation, "disabledIcon", { it.disabledIcon }, { pst, icn -> pst.disabledIcon = icn})
  }

  override fun getSeparatorColor(): Color {
    return JBColor.namedColor("MainToolbar.separatorColor", super.getSeparatorColor())
  }

  private fun findComboButton(c: Container): ComboBoxButton? {
    if (c is ComboBoxButton) return c

    for (child in c.components) {
      if (child is ComboBoxButton) {
        return child
      }
      val childCombo = (child as? Container)?.let { findComboButton(it) }
      if (childCombo != null) {
        return childCombo
      }
    }
    return null
  }

  override fun updateUI() {
    super.updateUI()
    updateFont()
  }

  override fun addImpl(comp: Component, constraints: Any?, index: Int) {
    super.addImpl(comp, constraints, index)
    comp.font = font
    if (comp is JComponent) {
      ClientProperty.put(comp, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  private fun updateFont() {
    font = JBUI.CurrentTheme.Toolbar.experimentalToolbarFont()
    for (component in components) {
      component.font = font
    }
  }
}

internal fun isToolbarInHeader() : Boolean {
  if (IdeFrameDecorator.isCustomDecorationAvailable()) {
    if (SystemInfoRt.isMac) {
      return true
    }
    val settings = UISettings.getInstance()
    if (SystemInfoRt.isWindows && !settings.separateMainMenu && settings.mergeMainMenuWithWindowTitle) {
      return true
    }
  }
  if (IdeRootPane.hideNativeLinuxTitle && !UISettings.getInstance().separateMainMenu) {
    return true
  }
  return false
}

internal fun isDarkHeader(): Boolean = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background"))

fun adjustIconForHeader(icon: Icon): Icon = if (isDarkHeader()) IconLoader.getDarkIcon(icon, true) else icon

private class HeaderIconUpdater {
  private val iconCache = ContainerUtil.createWeakSet<Icon>()

  private fun updateIcon(p: Presentation, getter: (Presentation) -> Icon?, setter: (Presentation, Icon) -> Unit) {
    if (!isDarkHeader()) return

    getter(p)?.let { icon ->
      val replaceIcon = adjustIconForHeader(icon)
      iconCache.add(replaceIcon)
      setter(p, replaceIcon)
    }
  }

  fun registerFor(presentation: Presentation, propName: String, getter: (Presentation) -> Icon?, setter: (Presentation, Icon) -> Unit) {
    updateIcon(presentation, getter, setter)
    presentation.addPropertyChangeListener(PropertyChangeListener { evt ->
      if (evt.propertyName != propName) return@PropertyChangeListener
      if (evt.newValue != null && evt.newValue in iconCache) return@PropertyChangeListener
      updateIcon(presentation, getter, setter)
    })
  }
}

private data class GroupInfo(val id: String, val name: String, val align: String)
