// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.codereview.ReturnToListComponent.createReturnToListSideComponent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTabComponentController
import javax.swing.*
import javax.swing.text.PlainDocument

internal class GHPRCreateComponentFactory(private val project: Project,
                                          private val dataContext: GHPRDataContext,
                                          private val viewController: GHPRToolWindowTabComponentController,
                                          disposable: Disposable) {

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(): JComponent {
    val infoTabInfo = TabInfo(createInfoComponent()).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent(GithubBundle.message("pull.request.back.to.list"), viewController::viewList)
    }

    return object : SingleHeightTabs(project, uiDisposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      addTab(infoTabInfo)
    }
  }

  private fun createInfoComponent(): JComponent {
    val repositoryDataService = dataContext.repositoryDataService

    val directionModel = GHPRCreateDirectionModelImpl(repositoryDataService.repositoryMapping)
    val titleDocument = PlainDocument()
    val descriptionDocument = PlainDocument()
    val metadataModel = GHPRCreateMetadataModel(repositoryDataService, dataContext.securityService.currentUser)
    val createLoadingModel = GHCompletableFutureLoadingModel<GHPullRequestShort>(uiDisposable)

    return GHPRCreateInfoComponentFactory(project, project.service(), dataContext, viewController)
      .create(directionModel, titleDocument, descriptionDocument, metadataModel, createLoadingModel)
  }
}