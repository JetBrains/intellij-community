// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.tabActions

import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ShadowAction
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.BadgeDotProvider
import com.intellij.ui.BadgeIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.BitUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import javax.swing.Icon
import javax.swing.JComponent

internal class CloseTab(component: JComponent,
                        private val file: VirtualFile,
                        private val editorWindow: EditorWindow,
                        parentDisposable: Disposable) : AnAction(), DumbAware {

  init {
    ShadowAction(this, IdeActions.ACTION_CLOSE, component, parentDisposable)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val pinned = isPinned()
    val modified = isModified()
    e.presentation.putClientProperty(JBEditorTabs.MARK_MODIFIED_KEY, modified)
    e.presentation.isVisible = UISettings.getInstance().showCloseButton || pinned || (ExperimentalUI.isNewUI() && modified)
    e.presentation.icon = getIcon(isHovered = false)
    e.presentation.hoveredIcon = getIcon(isHovered = true)

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
        shortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE)
        e.presentation.setText(IdeBundle.messagePointer("action.presentation.EditorTabbedContainer.text"))
      }
    }
  }

  private fun isPinned() = editorWindow.isFilePinned(file)

  private fun isModified() = UISettings.getInstance().markModifiedTabsWithAsterisk && editorWindow.getComposite(file)?.isModified == true

  /**
   * Whether to restrict the user to close the tab or not.
   * Restrict it only in new UI when a close button is not shown and
   * the file is not pinned and modified (blue dot shown in place of a close icon)
   */
  private fun isCloseActionRestricted() = ExperimentalUI.isNewUI() && !UISettings.getInstance().showCloseButton && !isPinned() && isModified()

  override fun actionPerformed(e: AnActionEvent) {
    if (isCloseActionRestricted()) {
      return
    }

    if (isPinned() && e.place == ActionPlaces.EDITOR_TAB) {
      if (Registry.`is`("ide.editor.tabs.interactive.pin.button")) {
        editorWindow.setFilePinned(file = file, pinned = false)
        ComponentUtil.getParentOfType(TabLabel::class.java, e.inputEvent?.component)?.updateTabActions()
      }
      return
    }

    val fileEditorManager = editorWindow.manager
    val window = if (ActionPlaces.EDITOR_TAB == e.place) editorWindow else fileEditorManager.currentWindow
    if (window != null) {
      if (e.inputEvent is MouseEvent && BitUtil.isSet(e.inputEvent!!.modifiersEx, InputEvent.ALT_DOWN_MASK)) {
        window.closeAllExcept(file)
      }
      else {
        fileEditorManager.closeFile(file = file, window = window)
      }
    }

    (editorWindow.tabbedPane.tabs as MorePopupAware).let {
      val popup = PopupUtil.getPopupContainerFor(e.inputEvent?.component)
      if (popup != null) {
        popup.cancel()
        if (it.canShowMorePopup()) {
          it.showMorePopup()
        }
      }
    }
  }

  fun getIcon(isHovered: Boolean): Icon {
    val pinned = isPinned()
    return if (!ExperimentalUI.isNewUI()) {
      when {
        pinned -> AllIcons.Actions.PinTab
        isHovered -> CLOSE_HOVERED_ICON
        else -> CLOSE_ICON
      }
    }
    else {
      if (isHovered && !pinned && UISettings.getInstance().showCloseButton) {
        CLOSE_HOVERED_ICON
      }
      else {
        val showModifiedIcon = isModified()
        when {
          showModifiedIcon -> {
            if (pinned) {
              val pinIcon = AllIcons.Actions.PinTab
              val provider = BadgeDotProvider(x = 0.7, y = 0.2, radius = 3.0 / pinIcon.iconWidth)
              BadgeIcon(pinIcon, JBUI.CurrentTheme.IconBadge.INFORMATION, provider)
            }
            else {
              DotIcon(JBUI.CurrentTheme.IconBadge.INFORMATION)
            }
          }
          pinned -> AllIcons.Actions.PinTab
          else -> CLOSE_ICON
        }
      }
    }
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

private val CLOSE_ICON: Icon
  get() = if (ExperimentalUI.isNewUI()) ExpUiIcons.General.CloseSmall else AllIcons.Actions.Close

private val CLOSE_HOVERED_ICON: Icon
  get() = (if (ExperimentalUI.isNewUI()) ExpUiIcons.General.CloseSmallHovered else AllIcons.Actions.CloseHovered)
