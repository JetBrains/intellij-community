// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.codereview.ReturnToListComponent
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent

internal class GHPRViewTabsFactory(private val project: Project,
                                   private val backToListAction: () -> Unit,
                                   private val disposable: Disposable) {

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(infoComponent: JComponent,
             diffController: GHPRDiffController,
             filesComponent: JComponent,
             filesCountModel: SingleValueModel<Int?>,
             commitsComponent: JComponent,
             commitsCountModel: SingleValueModel<Int?>): JBTabs {
    return create(infoComponent, filesComponent, filesCountModel, commitsComponent, commitsCountModel).also {
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
                     filesCountModel: SingleValueModel<Int?>,
                     commitsComponent: JComponent,
                     commitsCountModel: SingleValueModel<Int?>): JBTabs {

    val infoTabInfo = TabInfo(infoComponent).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent()
    }
    val filesTabInfo = TabInfo(filesComponent).apply {
      sideComponent = createReturnToListSideComponent()
    }.also {
      installTabTitleUpdater(it, GithubBundle.message("pull.request.files"), filesCountModel)
    }
    val commitsTabInfo = TabInfo(commitsComponent).apply {
      sideComponent = createReturnToListSideComponent()
    }.also {
      installTabTitleUpdater(it, GithubBundle.message("pull.request.commits"), commitsCountModel)
    }

    return object : SingleHeightTabs(project, uiDisposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      addTab(infoTabInfo)
      addTab(filesTabInfo)
      addTab(commitsTabInfo)
    }
  }

  private fun createReturnToListSideComponent(): JComponent {
    return ReturnToListComponent.createReturnToListSideComponent(GithubBundle.message("pull.request.back.to.list")) {
      backToListAction()
    }
  }

  private fun installTabTitleUpdater(tabInfo: TabInfo, @Nls title: String, countModel: SingleValueModel<Int?>) {
    countModel.addAndInvokeValueChangedListener {
      val count = countModel.value
      tabInfo.clearText(false).append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      if (count != null) tabInfo.append("  $count", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }
}