// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.PropertiesComponentImpl
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JComponent
import kotlin.math.min

private const val DO_NOT_ASK_KEY_PREFIX = "Notification.DoNotAsk-"

/**
 * @author Alexander Lobas
 */
class DoNotAskConfigurableUi {
  private var myCreated = false
  private lateinit var myList: JBList<DoNotAskInfo>
  private val myRemoveList = ArrayList<DoNotAskInfo>()

  private fun createDoNotAskList(): JBList<DoNotAskInfo> {
    return JBList(*getDoNotAskValues().toTypedArray()).apply {
      emptyText.clear()
      val projectTitle = IdeBundle.message("notifications.configurable.do.not.ask.project.title")
      cellRenderer = SimpleListCellRenderer.create("") { if (it.forProject) it.name + " (${projectTitle})" else it.name }
    }
  }

  fun getDoNotAskValues(): List<DoNotAskInfo> {
    val list = ArrayList<DoNotAskInfo>()

    getValues(PropertiesComponent.getInstance(), list, false)

    val project = getProject()
    if (project != null) {
      getValues(PropertiesComponent.getInstance(project), list, true)
    }

    list.sortWith(Comparator { o1, o2 -> o1.id.compareTo(o2.id) })

    return list
  }

  private fun getProject() =
    CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(WindowManager.getInstance().mostRecentFocusedWindow))

  private fun getValues(manager: PropertiesComponent, list: ArrayList<DoNotAskInfo>, forProject: Boolean) {
    if (manager is PropertiesComponentImpl) {
      for (key in manager.keys) {
        if (key.startsWith(DO_NOT_ASK_KEY_PREFIX)) {
          val id = key.substring(DO_NOT_ASK_KEY_PREFIX.length)
          val name = manager.getValue("Notification.DisplayName-DoNotAsk-$id", id)
          list.add(DoNotAskInfo(id, name, forProject))
        }
      }
    }
  }

  fun createComponent(): JComponent {
    myList = createDoNotAskList()
    myCreated = true

    return ToolbarDecorator.createDecorator(myList).disableUpDownActions().setRemoveAction {
      removeSelectedItems()
    }.createPanel()
  }

  private fun removeSelectedItems() {
    val indices = myList.selectedIndices
    val model = myList.model as DefaultListModel<DoNotAskInfo>

    for (index in indices.reversed()) {
      myRemoveList.add(model.getElementAt(index))
      model.remove(index)
    }

    val size = model.size()
    if (size > 0) {
      myList.selectedIndex = min(size - 1, indices.last())
    }
  }

  fun isModified(): Boolean {
    return myCreated && myRemoveList.isNotEmpty()
  }

  fun reset() {
    if (myCreated) {
      myRemoveList.clear()
      val selectedIndices = myList.selectedIndices
      myList.model = createDoNotAskList().model
      myList.selectedIndices = selectedIndices
    }
  }

  fun apply() {
    val manager = PropertiesComponent.getInstance()

    val project = getProject()
    val projectManager = if (project == null) null else PropertiesComponent.getInstance(project)

    for (info in myRemoveList) {
      if (info.forProject) {
        if (projectManager != null) {
          removeKey(projectManager, info.id)
        }
      }
      else {
        removeKey(manager, info.id)
      }
    }
  }

  private fun removeKey(manager: PropertiesComponent, id: String) {
    manager.unsetValue("Notification.DoNotAsk-$id")
    manager.unsetValue("Notification.DisplayName-DoNotAsk-$id")
  }
}

data class DoNotAskInfo(val id: String, val name: String, val forProject: Boolean)