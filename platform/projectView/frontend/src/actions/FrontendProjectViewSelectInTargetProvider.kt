// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTargetProvider
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl

internal class FrontendProjectViewSelectInTargetProvider : ProjectViewSelectInTargetProvider {
  override fun getSelectInTargets(project: Project): Collection<SelectInTarget> {
    if (!isProjectViewSplit()) return emptyList()
    val (panes, currentPane) = ProjectViewToolWindowServiceImpl.getInstance(project).panesSnapshot.value
    val currentPaneId = currentPane?.id
    val targets = createSelectInTargets(panes.map { it.descriptor as ProjectViewPaneDescriptorImpl })
    val (currentTargets, otherTargets) = targets.partition { currentPaneId in it.paneIds }
    return currentTargets.sortedBy { it.weight } + otherTargets.sortedBy { it.weight }
  }
}

/**
 * Creates the Select In targets of the [panes].
 *
 * A target without a group selects in its pane.
 * The targets of one group become the children of one [SplitProjectViewSelectInGroupTarget].
 * The group takes the place of its first target, and the first target decides the name and the weight of the group.
 */
internal fun createSelectInTargets(panes: List<ProjectViewPaneDescriptorImpl>): List<SplitProjectViewSelectInTarget> {
  val paneTargets = panes.flatMap { pane ->
    pane.selectInTargetDescriptors.map { descriptor ->
      descriptor.group to SplitProjectViewPaneSelectInTarget(pane.id, descriptor.presentableName, descriptor.weight)
    }
  }
  val subTargetsByGroupId = paneTargets
    .mapNotNull { (group, target) -> group?.let { it.id to target } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })
  val createdGroupIds = HashSet<String>()
  return paneTargets.mapNotNull { (group, target) ->
    when {
      group == null -> target
      createdGroupIds.add(group.id) -> SplitProjectViewSelectInGroupTarget(
        id = group.id,
        presentableName = group.presentableName,
        weight = group.weight,
        subTargets = subTargetsByGroupId.getValue(group.id),
      )
      else -> null
    }
  }
}
