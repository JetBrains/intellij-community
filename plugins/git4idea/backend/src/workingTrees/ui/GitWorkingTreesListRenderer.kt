// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.dvcs.ui.VcsRepositoryIconsProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripe
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripeSegment
import com.intellij.ui.hover.ListHoverListener
import java.awt.Color
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListModel

internal class GitWorkingTreesListRenderer(private val project: Project) : ListCellRenderer<GitWorkingTreesListEntry> {
  private val rowComponent = GitWorkingTreeRowComponent()

  override fun getListCellRendererComponent(
    list: JList<out GitWorkingTreesListEntry>,
    value: GitWorkingTreesListEntry,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val color = colorFor(value)
    val part = stripePart(list, index, color)
    val hovered = !isSelected && ListHoverListener.getHoveredIndex(list) == index
    return rowComponent.apply { configure(value, isSelected, hovered, cellHasFocus, list.font, color, part) }.component
  }

  private fun colorFor(entry: GitWorkingTreesListEntry): Color? {
    if (!entry.multiRoot) return null
    return VcsRepositoryIconsProvider.getInstance(project).getColor(entry.repository.repositoryId)
  }

  private fun stripePart(list: JList<out GitWorkingTreesListEntry>, index: Int, color: Color?): RepositoryColorStripeSegment {
    if (color == null) return RepositoryColorStripeSegment.SINGLE
    val model = list.model
    val repositoryId = repositoryIdAt(model, index)
    val prev = if (index > 0) repositoryIdAt(model, index - 1) else null
    val next = if (index < model.size - 1) repositoryIdAt(model, index + 1) else null
    return RepositoryColorStripe.resolveSegment(prev == repositoryId, next == repositoryId)
  }

  private fun repositoryIdAt(model: ListModel<out GitWorkingTreesListEntry>, index: Int) =
    model.getElementAt(index).repository.repositoryId
}
