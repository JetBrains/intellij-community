// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.RecentLocationsAction
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
import com.intellij.ui.BackgroundSupplier
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.SpeedSearchUtil.applySpeedSearchHighlighting
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

internal fun shortcutText(actionId: String) = ActionManager.getInstance().getKeyboardShortcut(actionId)?.let { getShortcutText(it) }

internal sealed interface SwitcherListItem {
  val mnemonic: String? get() = null
  val mainText: String
  val statusText: String get() = ""
  val pathText: String get() = ""
  val shortcutText: String? get() = null
  val separatorAbove: Boolean get() = false

  fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean)
  fun prepareExtraRenderer(component: SimpleColoredComponent, selected: Boolean) {
    shortcutText?.let {
      @Suppress("HardCodedStringLiteral")
      component.append(it, when (selected) {
        true -> SimpleTextAttributes.REGULAR_ATTRIBUTES
        else -> SimpleTextAttributes.SHORTCUT_ATTRIBUTES
      })
    }
  }
}

internal class SwitcherRecentLocations(val switcher: Switcher.SwitcherPanel) : SwitcherListItem {
  override val separatorAbove: Boolean = true
  override val mainText: String
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> message("recent.locations.changed.locations")
      else -> message("recent.locations.popup.title")
    }
  override val statusText: String
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> message("recent.files.accessible.open.recently.edited.locations")
      else -> message("recent.files.accessible.open.recently.viewed.locations")
    }
  override val shortcutText: String?
    get() = when (switcher.isOnlyEditedFilesShown) {
      true -> null
      else -> shortcutText(RecentLocationsAction.RECENT_LOCATIONS_ACTION_ID)
    }

  fun perform() {
    RecentLocationsAction.showPopup(switcher.project, switcher.isOnlyEditedFilesShown)
  }

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    component.iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    component.append(mainText)
  }
}

@Internal
data class SwitcherToolWindow(
  val window: ToolWindow,
  val id: String,
  val shortcut: Boolean,
  override val mnemonic: @NlsSafe String?,
) : SwitcherListItem {
  override val mainText: @NlsSafe String
    get() = window.stripeTitle
  override val statusText: @NlsSafe String
    get() = message("recent.files.accessible.show.tool.window", mainText)
  override val pathText: @NlsSafe String
    get() = ""
  override val shortcutText: @NlsSafe String?
    get() = if (shortcut) shortcutText(actionId) else null
  override val separatorAbove: Boolean
    get() = false

  private val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(window.id)

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    val defaultIcon = if (ExperimentalUI.isNewUI()) EmptyIcon.ICON_16 else EmptyIcon.ICON_13
    component.iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    val icon = if (ExperimentalUI.isNewUI()) window.icon else RenderingUtil.getIcon(window.icon, selected)
    component.icon = IconUtil.scaleByIconWidth(icon, null, defaultIcon)
    component.append(mainText)
  }
}

@Internal
class SwitcherVirtualFile : SwitcherListItem, BackgroundSupplier {
  constructor(rpcModel: SwitcherRpcDto.File) {
    mainText = rpcModel.mainText
    statusText = rpcModel.statusText
    pathText = rpcModel.pathText
    mnemonic = null
    shortcutText = null
    separatorAbove = false
    virtualFile = rpcModel.virtualFileId.virtualFile()
    backgroundColor = rpcModel.backgroundColorId?.color()
    foregroundTextColor = rpcModel.foregroundTextColorId?.color()
    icon = rpcModel.iconId.icon()
    hasProblems = rpcModel.hasProblems
  }

  constructor(localFile: VirtualFile, localIcon: Icon, project: Project) {
    mainText = localFile.name
    statusText = ""
    pathText = ""
    mnemonic = null
    shortcutText = null
    separatorAbove = false
    virtualFile = localFile
    backgroundColor = null
    foregroundTextColor = null
    icon = localIcon
    hasProblems = false
  }

  override val mainText: @NlsSafe String
  override val statusText: @NlsSafe String
  override val pathText: @NlsSafe String
  override val mnemonic: @NlsSafe String?
  override val shortcutText: @NlsSafe String?
  override val separatorAbove: Boolean
  val virtualFile: VirtualFile?
  val backgroundColor: Color?
  val foregroundTextColor: Color?
  val icon: Icon?
  val hasProblems: Boolean

  override fun getElementBackground(row: Int): Color? {
    return backgroundColor
  }

  override fun prepareMainRenderer(component: SimpleColoredComponent, selected: Boolean) {
    component.iconTextGap = JBUI.scale(4)
    component.icon = when (Registry.`is`("ide.project.view.change.icon.on.selection", true)) {
      true -> RenderingUtil.getIcon(icon, selected)
      else -> icon ?: AllIcons.Nodes.EmptyNode
    }
    val foreground = if (selected) null else foregroundTextColor
    val effectColor = if (hasProblems) JBColor.red else null
    val style = when (effectColor) {
      null -> SimpleTextAttributes.STYLE_PLAIN
      else -> SimpleTextAttributes.STYLE_PLAIN or SimpleTextAttributes.STYLE_WAVED
    }
    component.append(mainText, SimpleTextAttributes(style, foreground, effectColor))
    component.font?.let {
      val fontMetrics = component.getFontMetrics(it)
      val mainTextWidth = UIUtil.computeStringWidth(component, fontMetrics, mainText)
      val shortcutTextWidth = shortcutText?.let { shortcut -> UIUtil.computeStringWidth(component, fontMetrics, shortcut) } ?: 0
      val iconWidth = component.icon?.iconWidth ?: AllIcons.FileTypes.Text.iconWidth
      val width = component.width - mainTextWidth - shortcutTextWidth - component.iconTextGap - iconWidth -
                  component.insets.left - component.insets.right
      if (width <= 0) return@let null
      PaintUtil.cutContainerText(" ${pathText}", width, component)?.let { fragment ->
        @Suppress("HardCodedStringLiteral")
        component.append(fragment, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SwitcherVirtualFile) return false

    return this.virtualFile != null && this.virtualFile == other.virtualFile
  }

  override fun hashCode(): Int {
    return virtualFile?.hashCode() ?: Integer.MIN_VALUE
  }
}

internal class SwitcherListRenderer(val switcher: Switcher.SwitcherPanel) : ListCellRenderer<SwitcherListItem> {
  private val SEPARATOR = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()

  @Suppress("HardCodedStringLiteral") // to calculate preferred size
  private val mnemonic = JLabel("W").apply {
    preferredSize = preferredSize.apply { width += JBUI.CurrentTheme.ActionsList.mnemonicIconGap() }
    font = JBUI.CurrentTheme.ActionsList.applyStylesForNumberMnemonic(font)
  }
  private val main = SimpleColoredComponent().apply { isOpaque = false }
  private val extra = SimpleColoredComponent().apply { isOpaque = false }
  private val panel = CellRendererPanel(BorderLayout()).apply {
    add(BorderLayout.WEST, mnemonic)
    add(BorderLayout.CENTER, main)
    add(BorderLayout.EAST, extra)
  }

  override fun getListCellRendererComponent(
    list: JList<out SwitcherListItem>, value: SwitcherListItem, index: Int,
    selected: Boolean, focused: Boolean,
  ): Component {
    main.clear()
    extra.clear()

    val border = JBUI.Borders.empty(0, 10)
    panel.border = when (!selected && value.separatorAbove) {
      true -> JBUI.Borders.compound(border, JBUI.Borders.customLine(SEPARATOR, 1, 0, 0, 0))
      else -> border
    }
    RenderingUtil.getForeground(list, selected).let {
      mnemonic.foreground = if (selected) it else JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND
      main.foreground = it
      extra.foreground = it
    }
    mnemonic.text = value.mnemonic ?: ""
    mnemonic.isVisible = !switcher.recent && value.mnemonic != null
    value.prepareExtraRenderer(extra, selected)
    main.font = list.font
    val splitIconWidth = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width
    main.size = Dimension(list.width - extra.width - mnemonic.width - list.insets.left - list.insets.right - splitIconWidth, main.height)
    value.prepareMainRenderer(main, selected)
    applySpeedSearchHighlighting(switcher, main, false, selected)
    panel.accessibleContext.accessibleName = value.mainText
    panel.accessibleContext.accessibleDescription = value.statusText
    return panel
  }
}
