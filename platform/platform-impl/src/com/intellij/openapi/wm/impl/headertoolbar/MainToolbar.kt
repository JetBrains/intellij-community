// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.laf.darcula.ui.MainToolbarComboBoxButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import java.awt.*
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

internal class MainToolbar: JPanel(HorizontalLayout(10)) {

  private val disposable = Disposer.newDisposable()
  private val mainMenuButton: MainMenuButton?

  var layoutCallBack : LayoutCallBack? = null

  init {
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)
    isOpaque = true
    mainMenuButton = if (IdeRootPane.isMenuButtonInToolbar) MainMenuButton() else null
  }

  companion object {
    suspend fun computeActionGroups(): List<Pair<ActionGroup, String>> {
      val app = ApplicationManager.getApplication() as ComponentManagerEx
      app.getServiceAsync(ActionManager::class.java).await()
      val customActionSchema = app.getServiceAsync(CustomActionsSchema::class.java).await()
      return computeActionGroups(customActionSchema)
    }

    fun computeActionGroups(customActionSchema: CustomActionsSchema): List<Pair<ActionGroup, String>> {
      return sequenceOf(
        HorizontalLayout.LEFT to "MainToolbarLeft",
        HorizontalLayout.CENTER to "MainToolbarCenter",
        HorizontalLayout.RIGHT to "MainToolbarRight",
      )
        .mapNotNull { (position, id) ->
          (customActionSchema.getCorrectedAction(id) as ActionGroup?)?.let {
            it to position
          }
        }
        .toList()
    }
  }

  // Separate init because first, as part of IdeRootPane creation, we add bare component to allocate space and then,
  // as part of EDT task scheduled in a start-up activity, do fill it. That's to avoid flickering due to resizing.
  @RequiresEdt
  fun init(actionGroups: List<Pair<ActionGroup, String>>) {
    removeAll()

    mainMenuButton?.let {
      addWidget(it.button, HorizontalLayout.LEFT)
    }

    for ((actionGroup, position) in actionGroups) {
      addWidget(widget = createActionBar(actionGroup), position = position)
    }
  }

  override fun addNotify() {
    super.addNotify()
    mainMenuButton?.rootPane = rootPane
  }

  override fun removeNotify() {
    super.removeNotify()
    Disposer.dispose(disposable)
  }

  private fun addWidget(widget: JComponent, position: String) {
    add(position, widget)
    (widget as? Disposable)?.let { Disposer.register(disposable, it) }
  }

  private fun createActionBar(group: ActionGroup): JComponent {
    val toolbar = MyActionToolbarImpl(group, layoutCallBack)
    toolbar.setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))
    toolbar.setCustomButtonLook(HeaderToolbarButtonLook())

    toolbar.setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
    toolbar.targetComponent = null
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    val component = toolbar.component
    component.border = JBUI.Borders.empty()
    component.isOpaque = false
    return component
  }
}

typealias LayoutCallBack = () -> Unit

private class MyActionToolbarImpl(group: ActionGroup, val layoutCallBack: LayoutCallBack?) : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, group, true) {

  init {
    updateFont()
  }

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    super.calculateBounds(size2Fit, bounds)
    for (i in 0 until bounds.size) fitRectangle(bounds[i], getComponent(i))
  }

  override fun doLayout() {
    super.doLayout()
    layoutCallBack?.invoke()
  }

  private fun fitRectangle(rect: Rectangle, cmp: Component) {
    val minSize = ActionToolbar.experimentalToolbarMinimumButtonSize()
    if (!isSeparator(cmp)) rect.width = Integer.max(rect.width, minSize.width)
    rect.height = Integer.max(rect.height, minSize.height)
    rect.y = 0
  }

  override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    val component = super.createCustomComponent(action, presentation)

    if (component.foreground != null) {
      component.foreground = JBColor.namedColor("MainToolbar.foreground", component.foreground)
    }

    adjustIcons(presentation)

    if (action is ComboBoxAction) {
      findComboButton(component)?.apply {
        setUI(MainToolbarComboBoxButtonUI())
        addPropertyChangeListener("UI") { evt -> if (evt.newValue !is MainToolbarComboBoxButtonUI) setUI(MainToolbarComboBoxButtonUI())}
      }
    }
    return component
  }

  private fun adjustIcons(presentation: Presentation) {
    HeaderIconUpdater("icon",
                      { it.icon },
                      { pst, icn -> pst.icon = icn}
    ).updateIcon(presentation).subscribeTo(presentation)

    HeaderIconUpdater("selectedIcon",
                      { it.selectedIcon },
                      { pst, icn -> pst.selectedIcon = icn}
    ).updateIcon(presentation).subscribeTo(presentation)

    HeaderIconUpdater("hoveredIcon",
                      { it.hoveredIcon },
                      { pst, icn -> pst.hoveredIcon = icn}
    ).updateIcon(presentation).subscribeTo(presentation)

    HeaderIconUpdater("disabledIcon",
                      { it.disabledIcon },
                      { pst, icn -> pst.disabledIcon = icn}
    ).updateIcon(presentation).subscribeTo(presentation)
  }

  override fun getSeparatorColor(): Color {
    return JBColor.namedColor("MainToolbar.separatorColor", super.getSeparatorColor())
  }

  private fun findComboButton(c: Container): ComboBoxButton? {
    if (c is ComboBoxButton) return c

    for (child in c.components) {
      if (child is ComboBoxButton) return child
      val childCombo = (child as? Container)?.let { findComboButton(it) }
      if (childCombo != null) return childCombo
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
  }

  private fun updateFont() {
    font = JBUI.CurrentTheme.Toolbar.experimentalToolbarFont()
    for (component in components) {
      component.font = font
    }
  }

}

internal fun isToolbarInHeader(settings: UISettings = UISettings.shadowInstance) : Boolean {
  return IdeFrameDecorator.isCustomDecorationAvailable() &&
         (SystemInfoRt.isMac || (SystemInfoRt.isWindows && !settings.separateMainMenu && settings.mergeMainMenuWithWindowTitle))
}

internal fun isDarkHeader(): Boolean = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background"))

fun adjustIconForHeader(icon: Icon) = if (isDarkHeader()) IconLoader.getDarkIcon(icon, true) else icon

private class HeaderIconUpdater(val propName: String, val getter: (Presentation) -> Icon?, val setter: (Presentation, Icon) -> Unit) {
  private val iconsCache = HashMap<Icon, Icon>()

  fun updateIcon(p: Presentation): HeaderIconUpdater {
    if (!isDarkHeader()) return this

    getter(p)?.let { icon ->
      val replaceIcon = iconsCache.computeIfAbsent(icon) { adjustIconForHeader(it) }
      setter(p, replaceIcon) }

    return this
  }

  fun subscribeTo(presentation: Presentation): HeaderIconUpdater {
    presentation.addPropertyChangeListener(PropertyChangeListener { evt ->
      if (evt.propertyName != propName) return@PropertyChangeListener
      if (evt.newValue in iconsCache.values) return@PropertyChangeListener
      updateIcon(presentation)
    })

    return this
  }
}