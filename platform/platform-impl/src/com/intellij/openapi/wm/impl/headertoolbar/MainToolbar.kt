// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.laf.darcula.ui.MainToolbarComboBoxButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
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
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsets
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.image.RGBImageFilter
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

internal class MainToolbar: JPanel(HorizontalLayout(10)) {
  private val disposable = Disposer.newDisposable()
  private val mainMenuButton: MainMenuButton?

  init {
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)
    isOpaque = true
    mainMenuButton = if (IdeRootPane.isMenuButtonInToolbar) MainMenuButton() else null
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
}

private val lightThemeDarkHeaderDisableFilter: Supplier<RGBImageFilter> = Supplier {
  if (isDarkHeader()) UIUtil.GrayFilter(-70, -70, 100) else UIUtil.getGrayFilter()
}

private class MyActionToolbarImpl(group: ActionGroup) : ActionToolbarImpl(ActionPlaces.MAIN_TOOLBAR, group, true) {

  override fun calculateBounds(size2Fit: Dimension, bounds: MutableList<Rectangle>) {
    super.calculateBounds(size2Fit, bounds)
    for (i in 0 until bounds.size) fitRectangle(bounds[i], getComponent(i))
  }

  private fun fitRectangle(rect: Rectangle, cmp: Component) {
    val minSize = EXPERIMENTAL_TOOLBAR_MINIMUM_BUTTON_SIZE
    if (!isSeparator(cmp)) rect.width = Integer.max(rect.width, minSize.width)
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

  override fun applyToolbarLook(look: ActionButtonLook?, presentation: Presentation, component: JComponent) {
    presentation.putClientProperty(Presentation.DISABLE_ICON_FILTER, lightThemeDarkHeaderDisableFilter)
    super.applyToolbarLook(look, presentation, component)
  }
}

@JvmOverloads internal fun isToolbarInHeader(settings: UISettings = UISettings.shadowInstance) : Boolean {
  return ((SystemInfoRt.isMac && Registry.`is`("ide.experimental.ui.title.toolbar.in.macos", true))
          || (SystemInfoRt.isWindows && !settings.separateMainMenu && settings.mergeMainMenuWithWindowTitle)) && IdeFrameDecorator.isCustomDecorationAvailable()
}

internal fun isDarkHeader() = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background"))