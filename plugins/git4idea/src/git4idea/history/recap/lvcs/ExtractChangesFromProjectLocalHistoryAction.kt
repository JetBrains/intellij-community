// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history.recap.lvcs

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.diff.PresentableDifference
import git4idea.history.recap.RecapPatchesUtils
import java.io.StringWriter

internal class ExtractChangesFromProjectLocalHistoryAction : DumbAwareAction("Extract changes from Project Local History") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val activityFacade = project.service<ActivityFacade>()

    //TODO specify own from/to date
    val data = activityFacade.getActivity(from = 1748336164884, to = 1748339654286) ?: return

    val diffBeforeAndCurrent = activityFacade.getDiff(data, selectedItems = listOf(data.items.first()))
    diffBeforeAndCurrent?.printDiffDataAsPatches(project)

    val diffBeforeAndBefore = activityFacade.getDiff(data)
    diffBeforeAndBefore?.printDiffDataAsPatches(project)
  }

  private fun ActivityDiffData.getChanges(gateway: IdeaGateway?): List<Change> {
    return presentableChanges.filterIsInstance<PresentableDifference>().map { presentableDifference ->
      Change(
        presentableDifference.difference.getLeftContentRevision(gateway),
        presentableDifference.difference.getRightContentRevision(gateway)
      )
    }
  }

  private fun ActivityDiffData.printDiffDataAsPatches(project: Project) {
    val vcsChanges = getChanges(IdeaGateway.getInstance())

    val writer = StringWriter()
    RecapPatchesUtils.writePatches(project, vcsChanges, writer)
    println(writer.toString())
  }
}
