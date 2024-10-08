// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.VcsBookmarkRef
import java.awt.Color
import javax.swing.JComponent

internal class VcsLogLabelPainter(private val logData: VcsLogData, component: JComponent, iconCache: LabelIconCache) :
  LabelPainter(component, iconCache) {

  var showTagNames: Boolean = false

  fun customizePainter(references: Collection<VcsRef>,
                       bookmarks: Collection<VcsBookmarkRef>,
                       background: Color,
                       foreground: Color,
                       isSelected: Boolean,
                       availableWidth: Int) {
    val refGroups = getRefManager(logData, references)?.groupForTable(references, isCompact, showTagNames) ?: emptyList()
    customizePainter(background, foreground, isSelected, availableWidth, refGroups, bookmarks.sortedBy { it.type })
  }

  companion object {
    private fun getRefManager(logData: VcsLogData, references: Collection<VcsRef>): VcsLogRefManager? {
      val root = references.firstOrNull()?.root ?: return null
      return logData.getLogProvider(root).referenceManager
    }
  }
}
