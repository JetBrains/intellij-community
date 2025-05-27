// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history.recap.lvcs

import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.DirectoryDiffMode.WithLocal

@Service(Service.Level.PROJECT)
internal class ActivityFacade(private val project: Project) {

  private val activityProvider = LocalHistoryActivityProvider(project, IdeaGateway.getInstance())

  fun getActivity(from: Long, to: Long): ActivityData? {
    val timeFrame = from..to
    val activityScope = ActivityScope.fromFiles(project.getBaseDirectories())
    return activityProvider.loadActivityList(activityScope, null)
      .items.filter { it.takeIf { it.timestamp in timeFrame } != null }.takeIf { it.isNotEmpty() }?.let(::ActivityData)
  }

  /**
   * [selectedItems] - if 1 item, will return diff 'Before' <--> 'Current', if many will return 'Before' <--> 'Before' (interval diff)
   */
  fun getDiff(data: ActivityData, selectedItems: List<ActivityItem>? = null, diffMode: DirectoryDiffMode = WithLocal): ActivityDiffData? {
    val activityScope = ActivityScope.fromFiles(project.getBaseDirectories())
    val selection = ActivitySelection(selectedItems ?: data.items, data)

    val diffData = activityProvider.loadDiffData(activityScope, selection, diffMode)
    thisLogger().info("${diffData?.presentableChanges}")

    return diffData
  }
}
