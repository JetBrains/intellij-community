// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.commits.CommitNodeComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.graph.DefaultColorGenerator
import org.jetbrains.plugins.github.api.data.GHCommit
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class GHPRCommitsListCellRenderer : ListCellRenderer<VcsCommitMetadata> {

  private val nodeComponent = CommitNodeComponent().apply {
    foreground = DefaultColorGenerator().getColor(1)
  }
  private val messageComponent = SimpleColoredComponent()
  val panel = BorderLayoutPanel().addToLeft(nodeComponent).addToCenter(messageComponent)

  override fun getListCellRendererComponent(list: JList<out VcsCommitMetadata>,
                                            value: VcsCommitMetadata?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    messageComponent.clear()
    messageComponent.append(value?.subject.orEmpty(),
                            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, cellHasFocus)))
    SpeedSearchUtil.applySpeedSearchHighlighting(list, messageComponent, true, isSelected)

    val size = list.model.size
    when {
      size <= 1 -> nodeComponent.type = CommitNodeComponent.Type.SINGLE
      index == 0 -> nodeComponent.type = CommitNodeComponent.Type.FIRST
      index == size - 1 -> nodeComponent.type = CommitNodeComponent.Type.LAST
      else -> nodeComponent.type = CommitNodeComponent.Type.MIDDLE
    }
    panel.background = UIUtil.getListBackground(isSelected, cellHasFocus)
    return panel
  }
}