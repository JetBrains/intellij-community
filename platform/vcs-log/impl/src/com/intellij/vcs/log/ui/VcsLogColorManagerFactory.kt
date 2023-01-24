// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

object VcsLogColorManagerFactory {
  @JvmStatic
  fun create(roots: Set<VirtualFile>): VcsLogColorManager {
    return VcsLogColorManagerImpl(roots, CLASSIC_ROOT_COLORS)
  }

  @JvmStatic
  fun create(paths: Collection<FilePath>): VcsLogColorManager {
    return VcsLogColorManagerImpl(paths, CLASSIC_ROOT_COLORS)
  }

  private val CLASSIC_ROOT_COLORS: List<Color>
    get() = listOf(JBColor.RED, JBColor.GREEN, JBColor.BLUE, JBColor.ORANGE, JBColor.CYAN, JBColor.YELLOW, JBColor.MAGENTA, JBColor.PINK)
}