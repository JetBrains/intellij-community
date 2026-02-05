// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*package com.intellij.ui.icons

import org.jetbrains.icons.api.IconIdentifier
import org.jetbrains.icons.api.customIconIdentifier
import java.util.UUID

interface LegacySwingIcon : org.jetbrains.icons.api.Icon, javax.swing.Icon {
  val swingIcon: javax.swing.Icon

  override fun paintIcon(c: java.awt.Component, g: java.awt.Graphics, x: Int, y: Int) {
    swingIcon.paintIcon(c, g, x, y)
  }
  override fun getIconWidth(): Int = swingIcon.iconWidth
  override fun getIconHeight(): Int = swingIcon.iconHeight
}

interface LegacyIcon<TIcon: javax.swing.Icon> : LegacySwingIcon {
  override val swingIcon: TIcon
}

private class LegacyIconImpl<TIcon: javax.swing.Icon>(
  override val identifier: IconIdentifier,
  override val swingIcon: TIcon
): LegacyIcon<TIcon>

fun <TIcon: javax.swing.Icon> TIcon.legacyIcon(): LegacyIcon<TIcon> {
  val identifier = if (this is CachedImageIcon) {
    customIconIdentifier("::legacy/CachedImageIcon/")
  } else customIconIdentifier("::legacy/unknown/" + UUID.randomUUID().toString())
  return LegacyIconImpl(
    identifier,
    this
  )
}*/