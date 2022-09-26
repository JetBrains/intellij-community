// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.tabActions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ShadowAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.BitUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.Icon
import javax.swing.JComponent

class CloseTab(c: JComponent,
               val file: VirtualFile,
               val project: Project,
               val editorWindow: EditorWindow,
               parentDisposable: Disposable): AnAction(), DumbAware {

  init {
    ShadowAction(this, IdeActions.ACTION_CLOSE, c, parentDisposable)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val pinned = isPinned()

    if (ExperimentalUI.isNewUI()) {
      val showModifiedIcon = isModified()
      e.presentation.putClientProperty(JBEditorTabs.MARK_MODIFIED_KEY, showModifiedIcon)
      val icon = if (showModifiedIcon) {
        if (pinned) {
          val pinIcon = AllIcons.Actions.PinTab
          BadgeIcon(pinIcon, JBUI.CurrentTheme.IconBadge.INFORMATION, object : BadgeDotProvider() {
            override fun getX(): Double = 0.7

            override fun getY(): Double = 0.2

            override fun getRadius(): Double = 3.0 / pinIcon.iconWidth
          })
        }
        else DotIcon(JBUI.CurrentTheme.IconBadge.INFORMATION)
      }
      else if (pinned) {
        AllIcons.Actions.PinTab
      }
      else CLOSE_ICON

      e.presentation.isVisible = UISettings.getInstance().showCloseButton || pinned || showModifiedIcon
      e.presentation.icon = icon
      e.presentation.hoveredIcon = if (!pinned && UISettings.getInstance().showCloseButton) CLOSE_HOVERED_ICON else icon
    }
    else {
      e.presentation.isVisible = UISettings.getInstance().showCloseButton || pinned
      e.presentation.icon = if (!pinned) CLOSE_ICON else AllIcons.Actions.PinTab
      e.presentation.hoveredIcon = if (!pinned) CLOSE_HOVERED_ICON else AllIcons.Actions.PinTab
    }

    if (pinned && !Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean() || isCloseActionRestricted()) {
      e.presentation.text = ""
      shortcutSet = CustomShortcutSet.EMPTY
    }
    else {
      if (pinned) {
        shortcutSet = CustomShortcutSet.EMPTY
        e.presentation.text = TextWithMnemonic.parse(IdeBundle.message("action.unpin.tab")).dropMnemonic(true).text
      }
      else {
        shortcutSet = ObjectUtils.notNull(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE), CustomShortcutSet.EMPTY)
        e.presentation.setText(IdeBundle.messagePointer("action.presentation.EditorTabbedContainer.text"))
      }
    }
  }

  private fun isPinned() = editorWindow.isFilePinned(file)

  private fun isModified() = UISettings.getInstance().markModifiedTabsWithAsterisk && editorWindow.getComposite(file)?.isModified == true

  /**
   * Whether to restrict user to close the tab or not.
   * Restrict it only in new UI when close button is not shown and file is not pinned and modified (blue dot shown in place of close icon)
   */
  private fun isCloseActionRestricted() = ExperimentalUI.isNewUI() && !UISettings.getInstance().showCloseButton && !isPinned() && isModified()

  override fun actionPerformed(e: AnActionEvent) {
    if (isCloseActionRestricted()) {
      return
    }
    if (isPinned() && e.place == ActionPlaces.EDITOR_TAB) {
      if (Registry.get("ide.editor.tabs.interactive.pin.button").asBoolean()) {
        editorWindow.setFilePinned(file, false)
        ComponentUtil.getParentOfType(TabLabel::class.java, e.inputEvent?.component)?.updateTabActions()
      }
      return
    }

    val mgr = FileEditorManagerEx.getInstanceEx(project)
    val window: EditorWindow?
    if (ActionPlaces.EDITOR_TAB == e.place) {
      window = editorWindow
    }
    else {
      window = mgr.currentWindow
    }
    if (window != null) {
      if (e.inputEvent is MouseEvent && BitUtil.isSet(e.inputEvent.modifiersEx, InputEvent.ALT_DOWN_MASK)) {
        window.closeAllExcept(file)
      }
      else {
        if (window.getComposite(file) != null) {
          mgr.closeFile(file, window)
        }
      }
    }
    (editorWindow.tabbedPane.tabs as MorePopupAware).let {
      val popup = PopupUtil.getPopupContainerFor(e.inputEvent?.component)
      if (popup != null && it.canShowMorePopup()) {
        it.showMorePopup()
      }
      popup?.cancel()
    }
  }

  private class DotIcon(private val color: Color) : Icon {
    override fun getIconWidth() = JBUI.scale(13)

    override fun getIconHeight() = JBUI.scale(13)

    private val inset: Float
      get() = JBUIScale.scale(3.5f)

    private val diameter: Float
      get() = JBUIScale.scale(6.0f)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      val g2d = g.create() as Graphics2D
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val curInset = inset
      val curDiameter = diameter
      val circle = Ellipse2D.Float(x + curInset, y + curInset, curDiameter, curDiameter)

      g2d.color = color
      g2d.fill(circle)
      g2d.dispose()
    }
  }

  companion object {
    private val CLOSE_ICON = if (ExperimentalUI.isNewUI())
      IconManager.getInstance().getIcon("expui/general/closeSmall.svg", AllIcons::class.java) else AllIcons.Actions.Close

    private val CLOSE_HOVERED_ICON = if (ExperimentalUI.isNewUI())
      IconManager.getInstance().getIcon("expui/general/closeSmallHovered.svg", AllIcons::class.java) else AllIcons.Actions.CloseHovered
  }
}