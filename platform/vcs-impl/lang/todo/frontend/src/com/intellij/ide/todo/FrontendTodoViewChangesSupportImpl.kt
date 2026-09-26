// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.messages.MessageBusConnection
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JTree


internal class FrontendTodoViewChangesSupportImpl : TodoViewChangesSupport() {
  @Volatile
  private var state: TodoVcsState = TodoVcsState.EMPTY

  @Volatile
  private var listener: MyVcsListener? = null

  override fun isContentVisible(project: Project): Boolean {
    return state.isVisible
  }

  override fun getTabName(project: Project): String {
    return state.tabName
  }

  override fun createPanel(todoView: TodoView, settings: TodoPanelSettings, content: Content, factory: TodoTreeBuilderFactory): TodoPanel {
    val panel = object : FrontendChangeListTodosPanel(todoView, settings, content) {
      override fun createTreeBuilder(tree: JTree, project: Project): TodoTreeBuilder {
        val builder = factory.createTreeBuilder(tree, project)
        builder.init()
        return builder
      }
    }
    listener?.addPanel(panel)
    panel.applyState(state.tabName, state.fileIds)
    return panel
  }

  override fun createPanel(todoView: TodoView, settings: TodoPanelSettings, content: Content): TodoPanel {
    val panel = FrontendChangeListTodosPanel(todoView, settings, content)
    listener?.addPanel(panel)
    panel.applyState(state.tabName, state.fileIds)
    return panel
  }

  override fun installListener(project: Project,
                               connection: MessageBusConnection,
                               contentManagerFunc: () -> ContentManager?,
                               contentFunc: () -> Content): Listener {
    val scope = project.service<TodoCoroutineScopeProvider>().coroutineScope.childScope("SplitTodoViewChangesSupportImpl")
    val listener = MyVcsListener(project, scope, contentManagerFunc, contentFunc) { state = it }
    this.listener = listener
    Disposer.register(connection) {
      if (this.listener === listener) this.listener = null
      scope.cancel()
    }
    return listener
  }

  private class MyVcsListener(
    private val project: Project,
    scope: CoroutineScope,
    private val contentManagerFunc: () -> ContentManager?,
    private val contentFunc: () -> Content,
    private val updateState: (TodoVcsState) -> Unit) : Listener {

    private var myIsVisible = false
    private val myPanels = mutableListOf<FrontendChangeListTodosPanel>()

    init {
      scope.launch(Dispatchers.Default) {
        durable {
          val projectId = project.projectId()
          val api = ChangeListsApi.getInstance()
          combine(
            api.hasActiveVcss(projectId),
            api.areChangeListsEnabled(projectId),
            api.getChangeLists(projectId),
          ) { hasActiveVcss, areChangeListsEnabled, changeLists ->
            TodoVcsState.from(project, hasActiveVcss, areChangeListsEnabled, changeLists)
          }.distinctUntilChanged().collect { state ->
            updateState(state)
            withContext(Dispatchers.EDT) {
              stateChanged(state)
            }
          }
        }
      }
    }

    override fun setVisible(value: Boolean) {
      myIsVisible = value
    }

    fun addPanel(panel: FrontendChangeListTodosPanel) {
      myPanels.add(panel)
      Disposer.register(panel) {
        myPanels.remove(panel)
      }
    }

    private fun stateChanged(state: TodoVcsState) {
      myPanels.forEach { it.applyState(state.tabName, state.fileIds) }

      val contentManager = contentManagerFunc()
      if (contentManager == null || project.isDisposed) {
        // was not initialized yet
        return
      }

      val content = contentFunc()
      if (myIsVisible && !state.isVisible) {
        contentManager.removeContent(content, false)
        myIsVisible = false
      }
      else if (!myIsVisible && state.isVisible) {
        contentManager.addContent(content)
        myIsVisible = true
      }
    }
  }

  private data class TodoVcsState(
    val isVisible: Boolean,
    val tabName: @NlsContexts.TabTitle String,
    val fileIds: List<VirtualFileId>,
  ) {
    companion object {
      val EMPTY: TodoVcsState = TodoVcsState(
        isVisible = false,
        tabName = VcsBundle.message("todo.tab.title.all.changes"),
        fileIds = emptyList(),
      )

      fun from(
        project: Project,
        hasActiveVcss: Boolean,
        areChangeListsEnabled: Boolean,
        changeLists: List<ChangeListDto>,
      ): TodoVcsState {
        val localChangeLists = changeLists.map { it.getChangeList(project) }
        val defaultChangeList = localChangeLists.firstOrNull { it.isDefault }
        val changes = if (areChangeListsEnabled) {
          defaultChangeList?.changes.orEmpty()
        }
        else {
          localChangeLists.flatMap { it.changes }
        }
        return TodoVcsState(
          isVisible = hasActiveVcss,
          tabName = FrontendChangeListTodosPanel.getTabName(areChangeListsEnabled, defaultChangeList?.name),
          fileIds = changes.mapNotNull { it.virtualFile?.rpcId() }.distinct(),
        )
      }
    }
  }
}