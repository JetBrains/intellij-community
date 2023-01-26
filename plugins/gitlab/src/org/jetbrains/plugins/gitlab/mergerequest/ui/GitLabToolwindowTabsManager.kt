// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys

internal class GitLabToolwindowTabsManager private constructor(
  private val project: Project,
  private val contentManager: ContentManager
) {
  private val cs = contentManager.disposingMainScope()
  private val reviewTabsController = GitLabReviewTabsController(project)
  private val tabComponentFactory = GitLabReviewTabComponentFactory(project)

  init {
    contentManager.addDataProvider {
      when {
        GitLabMergeRequestsActionKeys.REVIEW_TABS_CONTROLLER.`is`(it) -> reviewTabsController
        else -> null
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      project.service<GitLabProjectConnectionManager>().connectionState.collect { connectionState ->
        if (connectionState == null) {
          setEmptyContent()
          return@collect
        }

        contentManager.removeAllContents(true)
        val newContent = createReviewListContent(connectionState)
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent)
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewTabsController.openReviewTabRequest.collect { reviewTab ->
        val currentConnection = project.service<GitLabProjectConnectionManager>().connectionState.value ?: return@collect
        when (reviewTab) {
          GitLabReviewTab.ReviewList -> {
            contentManager.setSelectedContent(contentManager.getContent(0)!!)
          }
          is GitLabReviewTab.ReviewSelected -> {
            selectExistedTabOrCreate(
              check = {
                (it as? GitLabReviewTab.ReviewSelected)?.reviewId?.iid == reviewTab.reviewId.iid
              },
              factory = {
                createReviewDetailsContent(currentConnection, reviewTab)
              }
            )
          }
        }
      }
    }
  }

  private fun createReviewDetailsContent(
    connection: GitLabProjectConnection,
    reviewTab: GitLabReviewTab.ReviewSelected
  ): Content = createReviewTabContent(connection, reviewTab) { content ->
    content.displayName = "#${reviewTab.reviewId.iid}"
  }

  private fun selectExistedTabOrCreate(
    check: (GitLabReviewTab) -> Boolean,
    factory: () -> Content
  ): Content {
    val existedTab = contentManager.contents.find { check(it.getUserData(GitLabReviewDataKeys.REVIEW_TAB)!!) }
    if (existedTab != null) {
      contentManager.setSelectedContent(existedTab)
      return existedTab
    }
    else {
      val reviewDetailsContent = factory()
      contentManager.addContent(reviewDetailsContent)
      contentManager.setSelectedContent(reviewDetailsContent)
      return reviewDetailsContent
    }
  }

  private fun createReviewListContent(
    connection: GitLabProjectConnection
  ): Content = createReviewTabContent(connection, GitLabReviewTab.ReviewList) { content ->
    content.isCloseable = false
    content.displayName = connection.repo.repository.projectPath.name
  }


  private fun createReviewTabContent(
    connection: GitLabProjectConnection,
    reviewTab: GitLabReviewTab,
    modifier: (Content) -> Unit
  ): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = true
    content.component = tabComponentFactory.createComponent(contentCs, connection, reviewTab)

    content.putUserData(GitLabReviewDataKeys.REVIEW_TAB, reviewTab)

    modifier(content)
  }

  private fun setEmptyContent() {
    contentManager.removeAllContents(true)
    val loginContent = createDisposableContent { content, contentCs ->
      content.component = tabComponentFactory.createEmptyContent(contentCs)
      content.isCloseable = false
    }
    contentManager.addContent(loginContent)
    contentManager.setSelectedContent(loginContent)
  }


  private fun createDisposableContent(modifier: (Content, CoroutineScope) -> Unit): Content {
    val factory = ContentFactory.getInstance()
    return factory.createContent(null, null, false).apply {
      val disposable = Disposer.newDisposable()
      setDisposer(disposable)
      modifier(this, disposable.disposingMainScope())
    }
  }

  companion object {
    fun showGitLabToolwindowContent(project: Project, contentManager: ContentManager) {
      GitLabToolwindowTabsManager(project, contentManager)
    }
  }
}