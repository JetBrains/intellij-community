// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log.command

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogTabsProperties
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.visible.*
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import java.util.function.Consumer

class GitLogCommandUiFactory(private val logId: String,
                             private val filters: VcsLogFilterCollection?,
                             private val uiProperties: VcsLogTabsProperties,
                             private val colorManager: VcsLogColorManager) : VcsLogManager.VcsLogUiFactory<MainVcsLogUi> {
  override fun createLogUi(project: Project, logData: VcsLogData): MainVcsLogUi {
    val vcsLogFilterer = GitLogCommandFilterer(project, logData.storage)
    val properties = uiProperties.createProperties(logId)
    val initialSortType = properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE)
    val initialFilters = filters ?: VcsLogFilterObject.collection()
    val refresher = VisiblePackRefresherImpl(project, logData, initialFilters, initialSortType, vcsLogFilterer, logId)
    return GitLogCommandUi(logId, logData, colorManager, properties, refresher, filters)
  }
}

class GitLogCommandUi(id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
                      uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
                      initialFilters: VcsLogFilterCollection?) :
  VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, initialFilters) {

  override fun createFilterUi(filterConsumer: Consumer<VcsLogFilterCollection>,
                              filters: VcsLogFilterCollection?,
                              parentDisposable: Disposable): VcsLogFilterUiEx {
    return GitLogCommandFilterUi(filters) { filterConsumer.accept(it) }
  }
}

