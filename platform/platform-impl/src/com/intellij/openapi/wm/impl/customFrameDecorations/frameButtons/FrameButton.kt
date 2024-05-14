// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicButtonUI

internal class FrameButton(action: Action, private val type: Type) : JButton(action) {

  enum class Type(val accessibleName: @Nls String) {
    MINIMIZE(IdeBundle.message("window.titleButton.iconify")),
    MAXIMIZE(IdeBundle.message("window.titleButton.maximize")),
    RESTORE(IdeBundle.message("window.titleButton.restore")),
    CLOSE(IdeBundle.message("window.titleButton.close"))
  }

  private enum class IconPack(val iconSets: Map<Type, IconSet>) {
    GNOME(
      mapOf(
        Type.MINIMIZE to IconSet(AllIcons.Linux.Theme.Gnome.Minimize, AllIcons.Linux.Theme.Gnome.MinimizeHover, AllIcons.Linux.Theme.Gnome.MinimizePressed, AllIcons.Linux.Theme.Gnome.MinimizeInactive),
        Type.MAXIMIZE to IconSet(AllIcons.Linux.Theme.Gnome.Maximize, AllIcons.Linux.Theme.Gnome.MaximizeHover, AllIcons.Linux.Theme.Gnome.MaximizePressed, AllIcons.Linux.Theme.Gnome.MaximizeInactive),
        Type.RESTORE to IconSet(AllIcons.Linux.Theme.Gnome.Restore, AllIcons.Linux.Theme.Gnome.RestoreHover, AllIcons.Linux.Theme.Gnome.RestorePressed, AllIcons.Linux.Theme.Gnome.RestoreInactive),
        Type.CLOSE to IconSet(AllIcons.Linux.Theme.Gnome.Close, AllIcons.Linux.Theme.Gnome.CloseHover, AllIcons.Linux.Theme.Gnome.ClosePressed, AllIcons.Linux.Theme.Gnome.CloseInactive)
      )
    )
  }

  private val listener = object : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      pressed = false
      updateStyle()
    }

    override fun mouseEntered(e: MouseEvent) {
      hovered = true
      updateStyle()
    }

    override fun mouseExited(e: MouseEvent) {
      hovered = false
      updateStyle()
    }

    override fun mousePressed(e: MouseEvent) {
      pressed = true
      updateStyle()
    }
  }

  private var iconPack = IconPack.GNOME
  private var darkHeader = false
  private var hovered = false
  private var pressed = false

  private val icons = mutableMapOf<Boolean, IconSet>()

  init {
    isFocusable = false
    text = null
    isOpaque = false
    putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, type.accessibleName)
    addMouseListener(listener)
    initIconPack()
  }

  override fun updateUI() {
    setUI(BasicButtonUI())
    border = null
    darkHeader = ColorUtil.isDark(JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true))
    updateStyle()
  }

  fun updateStyle() {
    // Can be null while initialization
    @Suppress("UNNECESSARY_SAFE_CALL")
    val iconSet = icons?.get(darkHeader)
    if (iconSet == null) {
      icon = null
      return
    }

    val frameActive = SwingUtilities.getWindowAncestor(this)?.isActive == true
    icon = when {
      !isEnabled -> iconSet.defaultIcon
      !frameActive -> iconSet.inactiveIcon
      pressed -> iconSet.pressedIcon
      hovered -> iconSet.hoverIcon
      else -> iconSet.defaultIcon
    }
  }

  fun updateTheme(theme: String?) {
    // todo recalculate iconPack if needed
  }

  private fun initIconPack() {
    val iconSet = iconPack.iconSets[type]!!

    for (dark in listOf(true, false)) {
      icons[dark] = IconSet(
        defaultIcon = IconLoader.getDarkIcon(iconSet.defaultIcon, dark),
        hoverIcon = IconLoader.getDarkIcon(iconSet.hoverIcon, dark),
        pressedIcon = IconLoader.getDarkIcon(iconSet.pressedIcon, dark),
        inactiveIcon = IconLoader.getDarkIcon(iconSet.inactiveIcon, dark),
      )
    }
  }
}

private data class IconSet(val defaultIcon: Icon, val hoverIcon: Icon, val pressedIcon: Icon, val inactiveIcon: Icon)