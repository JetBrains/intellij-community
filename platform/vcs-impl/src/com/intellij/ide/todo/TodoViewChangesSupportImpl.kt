// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.messages.MessageBusConnection
import javax.swing.JTree

class TodoViewChangesSupportImpl : TodoViewChangesSupport() {

  override fun isContentVisible(project: Project): Boolean {
    return ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
  }

  override fun getTabName(project: Project): String {
    return ChangeListTodosPanel.getTabName(project)
  }

  override fun createPanel(project: Project, settings: TodoPanelSettings, content: Content, factory: TodoTreeBuilderFactory): TodoPanel {
    return object : ChangeListTodosPanel(project, settings, content) {
      override fun createTreeBuilder(tree: JTree, project: Project): TodoTreeBuilder {
        val builder = factory.createTreeBuilder(tree, project)
        builder.init()
        return builder
      }
    }
  }

  override fun createPanel(project: Project, settings: TodoPanelSettings, content: Content): TodoPanel {
    return createPanel(project, settings, content, TodoTreeBuilderFactory { tree, p -> ChangeListTodosTreeBuilder(tree, p) })
  }

  override fun installListener(project: Project,
                               connection: MessageBusConnection,
                               contentManagerFunc: () -> ContentManager?,
                               contentFunc: () -> Content): Listener {
    val listener = MyVcsListener(project, contentManagerFunc, contentFunc)
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, listener)
    return listener
  }

  private class MyVcsListener(
    private val project: Project,
    private val contentManagerFunc: () -> ContentManager?,
    private val contentFunc: () -> Content) : VcsListener, Listener {

    private var myIsVisible = false

    override fun setVisible(value: Boolean) {
      myIsVisible = value;
    }

    override fun directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(
        {
          val contentManager = contentManagerFunc()
          if (contentManager == null || project.isDisposed) {
            // was not initialized yet
            return@invokeLater
          }

          val content = contentFunc()
          val hasActiveVcss = ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
          if (myIsVisible && !hasActiveVcss) {
            contentManager.removeContent(content, false)
            myIsVisible = false
          }
          else if (!myIsVisible && hasActiveVcss) {
            contentManager.addContent(content)
            myIsVisible = true
          }
        }, ModalityState.NON_MODAL)
    }
  }
}