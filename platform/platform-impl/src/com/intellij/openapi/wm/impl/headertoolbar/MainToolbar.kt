// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.laf.darcula.ui.MainToolbarComboBoxButtonUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.util.Disposer
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
    val toolbar = MyActionToolbarImpl(group = group)
    toolbar.setActionButtonBorder(JBUI.Borders.empty(mainToolbarButtonInsets()))
    toolbar.setCustomButtonLook(HeaderToolbarButtonLook())

    toolbar.setMinimumButtonSize(ActionToolbar.experimentalToolbarMinimumButtonSize())
    toolbar.targetComponent = null
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    val component = toolbar.component
    component.border = JBUI.Borders.empty()
    component.isOpaque = false
    return component
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
    val minSize = ActionToolbar.experimentalToolbarMinimumButtonSize()
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

internal fun isToolbarInHeader(settings: UISettings = UISettings.shadowInstance) : Boolean {
  return IdeFrameDecorator.isCustomDecorationAvailable() &&
         (SystemInfoRt.isMac || (SystemInfoRt.isWindows && !settings.separateMainMenu && settings.mergeMainMenuWithWindowTitle))
}

internal fun isDarkHeader(): Boolean = ColorUtil.isDark(JBColor.namedColor("MainToolbar.background"))