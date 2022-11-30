// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.codereview.CodeReviewTabs.bindTabText
import com.intellij.collaboration.ui.codereview.CodeReviewTabs.bindTabUi
import com.intellij.collaboration.ui.codereview.ReturnToListComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.SingleHeightTabs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.i18n.GithubBundle.messagePointer
import javax.swing.JComponent

internal class GHPRViewTabsFactory(private val project: Project,
                                   private val backToListAction: () -> Unit,
                                   private val disposable: Disposable) {

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement())
    .also { Disposer.register(uiDisposable) { it.cancel() } }

  fun create(infoComponent: JComponent,
             diffController: GHPRDiffController,
             filesComponent: JComponent,
             filesCountModel: Flow<Int?>,
             notViewedFilesCountModel: Flow<Int?>?,
             commitsComponent: JComponent,
             commitsCountModel: Flow<Int?>): JBTabs {
    return create(infoComponent, filesComponent, filesCountModel, notViewedFilesCountModel, commitsComponent, commitsCountModel).also {
      val listener = object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          diffController.activeTree = when (newSelection?.component) {
            filesComponent -> GHPRDiffController.ActiveTree.FILES
            commitsComponent -> GHPRDiffController.ActiveTree.COMMITS
            else -> null
          }
        }
      }
      it.addListener(listener)
      listener.selectionChanged(null, it.selectedInfo)
    }
  }

  private fun create(infoComponent: JComponent,
                     filesComponent: JComponent,
                     filesCountModel: Flow<Int?>,
                     notViewedFilesCountModel: Flow<Int?>?,
                     commitsComponent: JComponent,
                     commitsCountModel: Flow<Int?>): JBTabs {

    val infoTabInfo = TabInfo(infoComponent).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent()
    }
    val filesTabInfo = TabInfo(filesComponent).apply {
      sideComponent = createReturnToListSideComponent()
    }
    val commitsTabInfo = TabInfo(commitsComponent).apply {
      sideComponent = createReturnToListSideComponent()
    }.also {
      scope.bindTabText(it, messagePointer("pull.request.commits"), commitsCountModel)
    }

    val tabs = object : SingleHeightTabs(project, uiDisposable) {
      override fun adjust(each: TabInfo?) = Unit
    }.apply {
      addTab(infoTabInfo)
      addTab(filesTabInfo)
      addTab(commitsTabInfo)
    }

    // after adding to `JBTabs` as `getTabLabel()` is used in `bindTabUi`
    if (notViewedFilesCountModel == null) {
      scope.bindTabText(filesTabInfo, messagePointer("pull.request.files"), filesCountModel)
    }
    else {
      scope.bindTabUi(tabs, filesTabInfo, messagePointer("pull.request.files"), filesCountModel, notViewedFilesCountModel)
    }

    return tabs
  }

  private fun createReturnToListSideComponent(): JComponent {
    return ReturnToListComponent.createReturnToListSideComponent(GithubBundle.message("pull.request.back.to.list")) {
      backToListAction()
    }
  }
}