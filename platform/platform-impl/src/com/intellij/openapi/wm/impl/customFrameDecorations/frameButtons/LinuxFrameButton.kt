// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UnixDesktopEnv
import org.jetbrains.annotations.Nls
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicButtonUI

internal class LinuxFrameButton(action: Action, private val type: Type) : JButton(action) {

  enum class Type(val text: @Nls String) {
    MINIMIZE(IdeBundle.message("window.titleButton.minimize")),
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
    ),
    KDE(
      mapOf(
        Type.MINIMIZE to IconSet(AllIcons.Linux.Theme.Kde.Minimize, AllIcons.Linux.Theme.Kde.MinimizeHover, AllIcons.Linux.Theme.Kde.MinimizePressed, AllIcons.Linux.Theme.Kde.MinimizeInactive),
        Type.MAXIMIZE to IconSet(AllIcons.Linux.Theme.Kde.Maximize, AllIcons.Linux.Theme.Kde.MaximizeHover, AllIcons.Linux.Theme.Kde.MaximizePressed, AllIcons.Linux.Theme.Kde.MaximizeInactive),
        Type.RESTORE to IconSet(AllIcons.Linux.Theme.Kde.Restore, AllIcons.Linux.Theme.Kde.RestoreHover, AllIcons.Linux.Theme.Kde.RestorePressed, AllIcons.Linux.Theme.Kde.RestoreInactive),
        Type.CLOSE to IconSet(AllIcons.Linux.Theme.Kde.Close, AllIcons.Linux.Theme.Kde.CloseHover, AllIcons.Linux.Theme.Kde.ClosePressed, AllIcons.Linux.Theme.Kde.CloseInactive)
      )
    )
  }

  companion object {
    private val gnomeSimilarIconThemes = listOf("adwaita", "yaru")
    private val kdeSimilarIconThemes = listOf("breeze")

    private fun getDefaultIconPack(): IconPack =
      if (UnixDesktopEnv.CURRENT == UnixDesktopEnv.KDE) IconPack.KDE else IconPack.GNOME
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

  private var iconPack = getDefaultIconPack()
  private var darkHeader = false
  private var hovered = false
  private var pressed = false

  private val icons = mutableMapOf<Boolean, IconSet>()

  init {
    isFocusable = false
    text = null
    isOpaque = false
    toolTipText = type.text
    putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, type.text)
    addMouseListener(listener)
    initIconPack()
  }

  override fun updateUI() {
    setUI(BasicButtonUI())
    border = null
    darkHeader = ColorUtil.isDark(InternalUICustomization.getInstance()?.getMainToolbarBackground(true) ?: JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true))
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

  fun updateIconTheme(iconTheme: String?) {
    var suggestedIconTheme: IconPack? = null
    if (iconTheme != null) {
      if (isSimilarIconTheme(iconTheme, gnomeSimilarIconThemes)) {
        suggestedIconTheme = IconPack.GNOME
      }
      else if (isSimilarIconTheme(iconTheme, kdeSimilarIconThemes)) {
        suggestedIconTheme = IconPack.KDE
      }
    }

    val newIconPack = suggestedIconTheme ?: getDefaultIconPack()
    if (iconPack != newIconPack) {
      iconPack = newIconPack
      initIconPack()
    }
  }

  private fun isSimilarIconTheme(iconTheme: String, similarIconThemes: List<String>): Boolean {
    for (theme in similarIconThemes) {
      if (iconTheme.startsWith(theme, ignoreCase = true)) {
        return true
      }
    }
    return false
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
