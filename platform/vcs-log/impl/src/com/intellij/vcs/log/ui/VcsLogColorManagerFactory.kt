// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object VcsLogColorManagerFactory {
  @JvmStatic
  fun create(roots: Set<VirtualFile>): VcsLogColorManager {
    if (ExperimentalUI.isNewUI()) return VcsLogColorManagerImpl(roots, NEW_UI_ROOT_COLORS_CLOSED_STATE)
    return ClassicUiVcsLogColorManager.forRoots(roots)
  }

  @JvmStatic
  fun create(paths: Collection<FilePath>): VcsLogColorManager {
    if (ExperimentalUI.isNewUI()) return VcsLogColorManagerImpl(paths, NEW_UI_ROOT_COLORS_CLOSED_STATE)
    return ClassicUiVcsLogColorManager.forPaths(paths)
  }
}

private class ClassicUiVcsLogColorManager(private val delegate: VcsLogColorManager) : VcsLogColorManager by delegate {
  override fun getPathColor(path: FilePath): Color = delegate.getPathColor(path).mixWithTableBackground()

  override fun getRootColor(root: VirtualFile): Color = delegate.getRootColor(root).mixWithTableBackground()

  private fun Color.mixWithTableBackground(): Color = JBColor.lazy { ColorUtil.mix(this, UIUtil.getTableBackground(), 0.75) }

  companion object {
    fun forRoots(roots: Set<VirtualFile>): VcsLogColorManager =
      ClassicUiVcsLogColorManager(VcsLogColorManagerImpl(roots, CLASSIC_ROOT_COLORS))

    fun forPaths(paths: Collection<FilePath>): VcsLogColorManager =
      ClassicUiVcsLogColorManager(VcsLogColorManagerImpl(paths, CLASSIC_ROOT_COLORS))

    private val CLASSIC_ROOT_COLORS: List<Color>
      get() = listOf(JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK)
  }
}

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
