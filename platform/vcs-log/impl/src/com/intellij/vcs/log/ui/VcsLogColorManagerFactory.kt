// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorGenerator
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorGeneratorFactory
import com.intellij.ui.JBColor
import com.intellij.vcsUtil.VcsUtil
import java.awt.Color

object VcsLogColorManagerFactory {
  const val ROOT_OPENED_STATE: String = "NEW_UI_ROOT_OPEN_STATE"

  @JvmStatic
  fun create(roots: Set<VirtualFile>): VcsLogColorManager = create(convertRootsToPaths(roots))

  @JvmStatic
  fun create(paths: Collection<FilePath>): VcsLogColorManager = VcsLogColorManagerImpl(paths, NEW_UI_ROOTS_OPENED_COLOR_SPACE)

  private fun convertRootsToPaths(roots: Collection<VirtualFile>): List<FilePath> = roots
    .sortedBy { it.name }
    .map(VcsUtil::getFilePath)

  private val NEW_UI_ROOTS_OPENED_COLOR_SPACE: RepositoryColorGenerator.AdditionalColorSpace
    get() = RepositoryColorGenerator.AdditionalColorSpace(ROOT_OPENED_STATE, NEW_UI_ROOT_COLORS_OPENED_STATE)

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
}

private class VcsLogColorManagerImpl(
  paths: Collection<FilePath>,
  vararg additionalColorSpaces: RepositoryColorGenerator.AdditionalColorSpace,
) : VcsLogColorManager {
  private val filePaths: List<FilePath> = paths.toList()
  private val pathIds: List<String> = filePaths.map { it.getPath() }

  private val logColorGenerator = RepositoryColorGeneratorFactory.create(pathIds, *additionalColorSpaces)

  override fun getPathColor(path: FilePath, colorSpace: String): Color {
    return logColorGenerator.getColor(path.path, colorSpace)
  }

  override fun getRootColor(root: VirtualFile, colorSpace: String): Color {
    return logColorGenerator.getColor(root.getPath(), colorSpace)
  }

  override fun getPaths(): Collection<FilePath> = filePaths
}