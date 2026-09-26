// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.vcs.impl.shared.SHOW_COMMIT_CHECK_TODOS_REMOTE_TOPIC
import com.intellij.platform.vcs.impl.shared.ShowCommitCheckTodosRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


internal class CommitCheckTodosRemoteTopicListener : ProjectRemoteTopicListener<ShowCommitCheckTodosRequest> {
  override val topic: ProjectRemoteTopic<ShowCommitCheckTodosRequest> = SHOW_COMMIT_CHECK_TODOS_REMOTE_TOPIC

  override fun handleEvent(project: Project, event: ShowCommitCheckTodosRequest) {
    project.service<TodoCoroutineScopeProvider>().coroutineScope.launch(Dispatchers.EDT) {
      val content = project.service<TodoView>().addCustomTodoView(
        { tree, treeProject -> FrontendChangeListTodosTreeBuilder(tree, treeProject).apply { setFiles(event.fileIds) } },
        event.title,
        TodoPanelSettings(VcsConfiguration.getInstance(project).myTodoPanelSettings),
      ) ?: return@launch

      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.TODO_VIEW) ?: return@launch
      toolWindow.show { toolWindow.contentManager.setSelectedContent(content, true) }
    }
  }
}