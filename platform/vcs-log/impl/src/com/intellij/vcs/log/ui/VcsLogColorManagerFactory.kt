// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import java.awt.Color

object VcsLogColorManagerFactory {
  const val ROOT_OPENED_STATE: String = "NEW_UI_ROOT_OPEN_STATE"

  @JvmStatic
  fun create(roots: Set<VirtualFile>): VcsLogColorManager = create(convertRootsToPaths(roots))

  @JvmStatic
  fun create(paths: Collection<FilePath>): VcsLogColorManager = VcsLogColorManagerSwitcher(paths)

  private fun convertRootsToPaths(roots: Collection<VirtualFile>): List<FilePath> = roots
    .sortedBy { it.name }
    .map(VcsUtil::getFilePath)
}

private class VcsLogColorManagerSwitcher(private val paths: Collection<FilePath>) : VcsLogColorManager {
  private var isNewUI: Boolean = ExperimentalUI.isNewUI()

  private var colorManager: VcsLogColorManager

  init {
    colorManager = createColorManager()
  }

  private fun createColorManager(): VcsLogColorManager {
    return if (isNewUI) NewUiVcsLogColorManager(paths) else ClassicUiVcsLogColorManager(paths)
  }

  private fun updateColorManagerIfNeeded() {
    if (isNewUI == ExperimentalUI.isNewUI()) return

    isNewUI = ExperimentalUI.isNewUI()
    colorManager = createColorManager()
  }

  override fun getPathColor(path: FilePath, colorMode: String): Color {
    updateColorManagerIfNeeded()
    return colorManager.getPathColor(path, colorMode)
  }

  override fun getLongName(path: FilePath): String = colorManager.getLongName(path)

  override fun hasMultiplePaths(): Boolean = colorManager.hasMultiplePaths()

  override fun getPaths(): Collection<FilePath> = colorManager.paths
}

private class ClassicUiVcsLogColorManager(paths: Collection<FilePath>) : VcsLogColorManager {
  private val delegate: VcsLogColorManager = VcsLogColorManagerImpl(paths, CLASSIC_ROOT_COLORS)

  override fun getPathColor(path: FilePath, colorMode: String): Color = delegate.getPathColor(path, colorMode).mixWithTableBackground()

  override fun getPaths(): Collection<FilePath> = delegate.paths

  private fun Color.mixWithTableBackground(): Color = JBColor.lazy { ColorUtil.mix(this, UIUtil.getTableBackground(), 0.75) }

  companion object {
    private val CLASSIC_ROOT_COLORS: List<Color>
      get() = listOf(JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK)
  }
}

private class NewUiVcsLogColorManager(paths: Collection<FilePath>) : VcsLogColorManager {
  private val delegate: VcsLogColorManager = VcsLogColorManagerImpl(paths, NEW_UI_ROOT_COLORS_CLOSED_STATE, NEW_UI_ROOTS_OPENED_COLOR_SPACE)

  override fun getPathColor(path: FilePath, colorMode: String): Color = delegate.getPathColor(path, colorMode)

  override fun getPaths(): Collection<FilePath> = delegate.paths

  private val NEW_UI_ROOT_COLORS_CLOSED_STATE: List<Color>
    get() = listOf(
      JBColor(0xFF6285, 0x865762),
      JBColor(0x89C398, 0x436946),
      JBColor(0x6D5FF3, 0x5B53AF),
      JBColor(0xF2B181, 0x825845),
      JBColor(0x32E0D9, 0x389895),
      JBColor(0xFED277, 0x826A41),
      JBColor(0xF95CFF, 0x88458A),
      JBColor(0xBE906D, 0x947259),
      JBColor(0x4FF2B7, 0x178E63),
      JBColor(0xA177F4, 0x583D7A),
      JBColor(0x3FB3B8, 0x227985),
      JBColor(0xB979B9, 0x875887),
      JBColor(0xECCA88, 0x8C7B58),
      JBColor(0xC9CCD6, 0x5A5D6B),
    )

  private val NEW_UI_ROOT_COLORS_OPENED_STATE: List<Color>
    get() = listOf(
      JBColor(0xFFE3EA, 0x603239),
      JBColor(0xE3F7E7, 0x375239),
      JBColor(0xE3E1F7, 0x383275),
      JBColor(0xFFEFE3, 0x614438),
      JBColor(0xD5ECEB, 0x1D4D4A),
      JBColor(0xFFF5DB, 0x5E4D33),
      JBColor(0xFEE4FF, 0x562B58),
      JBColor(0xE8E2DD, 0x624E3F),
      JBColor(0xD5F2E8, 0x0D573C),
      JBColor(0xEFE5FF, 0x3B3147),
      JBColor(0xDAF4F5, 0x204145),
      JBColor(0xECE0EC, 0x503250),
      JBColor(0xFDF0D7, 0x594D36),
      JBColor(0xEBECF0, 0x494B57),
    )

  private val NEW_UI_ROOTS_OPENED_COLOR_SPACE: VcsLogColorManagerImpl.AdditionalColorSpace
    get() = VcsLogColorManagerImpl.AdditionalColorSpace(VcsLogColorManagerFactory.ROOT_OPENED_STATE, NEW_UI_ROOT_COLORS_OPENED_STATE)
}