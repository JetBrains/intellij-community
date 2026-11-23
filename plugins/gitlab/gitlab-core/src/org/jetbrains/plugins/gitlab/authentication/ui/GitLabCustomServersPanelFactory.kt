// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.ui.util.toListModelIn
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.CustomServer
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

private const val LIST_VISIBLE_ROW_COUNT = 3

internal object GitLabCustomServersPanelFactory {
  fun create(project: Project, cs: CoroutineScope, vm: GitLabCustomServersViewModel): JComponent {
    val list = JBList(vm.servers.toListModelIn(cs)).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      visibleRowCount = LIST_VISIBLE_ROW_COUNT
      cellRenderer = textListCellRenderer<CustomServer> { it.serverUri }
      emptyText.text = GitLabBundle.message("settings.oauth.custom.server.list.empty")
    }
    val controller = CustomServerListController(cs, project, vm, list)
    val listWithToolbar = ToolbarDecorator.createDecorator(list)
      .setAddAction { controller.addServer() }
      .setEditAction { controller.editSelectedServer() }
      .setRemoveAction { controller.removeSelectedServer() }
      .disableUpDownActions()
      .createPanel()

    return listWithToolbar
  }
}

private class CustomServerListController(
  private val cs: CoroutineScope,
  private val project: Project,
  private val vm: GitLabCustomServersViewModel,
  private val list: JList<CustomServer>,
) {
  fun addServer() {
    val server = GitLabConfigureCustomServerDialog.show(cs, project, list, null) ?: return
    vm.add(server)
    list.setSelectedValue(server, true)
  }

  fun editSelectedServer() {
    val initial = list.selectedValue ?: return
    val index = vm.servers.value.indexOf(initial).takeIf { it >= 0 } ?: return
    val server = GitLabConfigureCustomServerDialog.show(cs, project, list, initial) ?: return
    vm.replace(index, server)
    list.setSelectedValue(server, true)
  }

  fun removeSelectedServer() {
    val selected = list.selectedValue ?: return
    val index = vm.servers.value.indexOf(selected).takeIf { it >= 0 } ?: return
    vm.remove(index)
    val server = vm.servers.value.let { it.getOrNull(minOf(index, it.lastIndex)) } ?: return
    list.setSelectedValue(server, true)
    list.requestFocusInWindow()
  }
}
