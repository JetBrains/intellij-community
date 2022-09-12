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
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal class MainToolbar: JPanel(HorizontalLayout(10)) {

  private val visibleComponentsPool = VisibleComponentsPool()
  private val disposable = Disposer.newDisposable()
  private val mainMenuButton: MainMenuButton?

  init {
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)
    isOpaque = true
    if (IdeRootPane.isMenuButtonInToolbar()) {
      mainMenuButton = MainMenuButton()
      Disposer.register(disposable, mainMenuButton.menuShortcutHandler)
    }
    else {
      mainMenuButton = null
    }
  }

  // Separate init because first, as part of IdeRootPane creation, we add bare component to allocate space and then,
  // as part of EDT task scheduled in a start-up activity, do fill it. That's to avoid flickering due to resizing.
  fun init(project: Project?) {
    mainMenuButton?.let {
      addWidget(it.button, HorizontalLayout.LEFT)
    }

    ActionManagerEx.withLazyActionManager(project?.coroutineScope ?: ApplicationManager.getApplication()?.coroutineScope) {
      val customActionSchema = CustomActionsSchema.getInstance()
      createActionBar("MainToolbarLeft", customActionSchema)?.let { addWidget(it, HorizontalLayout.LEFT) }
      createActionBar("MainToolbarCenter", customActionSchema)?.let { addWidget(it, HorizontalLayout.CENTER) }
      createActionBar("MainToolbarRight", customActionSchema)?.let { addWidget(it, HorizontalLayout.RIGHT) }
    }
    addComponentListener(ResizeListener())
  }

  override fun addNotify() {
    super.addNotify()
    mainMenuButton?.menuShortcutHandler?.registerShortcuts(rootPane)
  }

  override fun removeNotify() {
    super.removeNotify()
    mainMenuButton?.menuShortcutHandler?.unregisterShortcuts()
    Disposer.dispose(disposable)
  }

  private fun addWidget(widget: JComponent, position: String) {
    add(position, widget)
    visibleComponentsPool.addElement(widget, position)
    (widget as? Disposable)?.let { Disposer.register(disposable, it) }
  }

  private fun createActionBar(groupId: String, customActionSchema: CustomActionsSchema): JComponent? {
    val toolbar = createToolbar(groupId, customActionSchema) ?: return null
    toolbar.setMinimumButtonSize(ActionToolbar.EXPERIMENTAL_TOOLBAR_MINIMUM_BUTTON_SIZE)
    toolbar.targetComponent = null
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    val component = toolbar.component
    component.border = JBUI.Borders.empty()
    component.isOpaque = false
    return component
  }

  private fun createToolbar(groupId: String, schema: CustomActionsSchema): ActionToolbar? {
    val group = schema.getCorrectedAction(groupId) as ActionGroup? ?: return null

    return MyActionToolbarImpl(group).apply {
      setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))
      setCustomButtonLook(HeaderToolbarButtonLook())
    }
  }

  private inner class ResizeListener : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      val visibleElementsWidth = components.asSequence().filter { it.isVisible }.sumOf { it.preferredSize.width }
      val componentWidth = size.width
      if (visibleElementsWidth > componentWidth) {
        decreaseVisibleSizeBy(visibleElementsWidth - componentWidth)
      }
      else {
        increaseVisibleSizeBy(componentWidth - visibleElementsWidth)
      }
    }

    private fun increaseVisibleSizeBy(delta: Int) {
      var restDelta = delta
      var comp = visibleComponentsPool.nextToShow()
      while (comp != null && restDelta > 0) {
        val width = comp.preferredSize.width
        if (width > restDelta) return
        comp.isVisible = true
        restDelta -= width
        comp = visibleComponentsPool.nextToShow()
      }
    }

    private fun decreaseVisibleSizeBy(delta: Int) {
      var restDelta = delta
      var comp = visibleComponentsPool.nextToHide()
      while (comp != null && restDelta > 0) {
        comp.isVisible = false
        restDelta -= comp.preferredSize.width
        comp = visibleComponentsPool.nextToHide()
      }
    }
  }
}

private class MyActionToolbarImpl(group: ActionGroup) : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, group, true) {

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) = super.calculateBounds(size2Fit, bounds).apply {
    bounds.forEach { fitRectangle(it) }
  }

  private fun fitRectangle(rect: Rectangle) {
    val minSize = EXPERIMENTAL_TOOLBAR_MINIMUM_BUTTON_SIZE
    rect.width = Integer.max(rect.width, minSize.width)
    rect.height = Integer.max(rect.height, minSize.height)
    rect.y = 0
  }

  override fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    val component = super.createCustomComponent(action, presentation)
    if (action is ComboBoxAction) {
      findComboButton(component)?.setUI(MainToolbarComboBoxButtonUI())
    }
    return component
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
}

private class VisibleComponentsPool {
  val elements = mapOf<String, MutableList<JComponent>>(
    HorizontalLayout.LEFT to mutableListOf(),
    HorizontalLayout.RIGHT to mutableListOf(),
    HorizontalLayout.CENTER to mutableListOf()
  )

  fun addElement(comp: JComponent, position: String) = elements[position]!!.add(comp)

  fun nextToShow(): JComponent? {
    return elements[HorizontalLayout.CENTER]!!.firstOrNull { !it.isVisible }
           ?: elements[HorizontalLayout.RIGHT]!!.firstOrNull { !it.isVisible }
           ?: elements[HorizontalLayout.LEFT]!!.firstOrNull { !it.isVisible }
  }

  fun nextToHide(): JComponent? {
    return elements[HorizontalLayout.LEFT]!!.lastOrNull { it.isVisible }
           ?: elements[HorizontalLayout.RIGHT]!!.lastOrNull { it.isVisible }
           ?: elements[HorizontalLayout.CENTER]!!.lastOrNull { it.isVisible }
  }
}

@JvmOverloads internal fun isToolbarInHeader(settings: UISettings = UISettings.shadowInstance) : Boolean {
  return ((SystemInfoRt.isMac && Registry.`is`("ide.experimental.ui.title.toolbar.in.macos", true))
          || (SystemInfoRt.isWindows && !settings.separateMainMenu && settings.mergeMainMenuWithWindowTitle)) && IdeFrameDecorator.isCustomDecorationAvailable()
}