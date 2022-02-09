// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.BasePropertyService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JComponent
import kotlin.math.min

private const val DO_NOT_ASK_KEY_PREFIX = "Notification.DoNotAsk-"

internal class DoNotAskConfigurableUi {
  private var myCreated = false
  private lateinit var myList: JBList<DoNotAskInfo>
  private val myRemoveList = ArrayList<DoNotAskInfo>()

  private fun createDoNotAskList(): JBList<DoNotAskInfo> {
    val result = JBList(*getDoNotAskValues().toTypedArray())
    result.emptyText.clear()
    val projectTitle = IdeBundle.message("notifications.configurable.do.not.ask.project.title")
    result.cellRenderer = SimpleListCellRenderer.create("") { if (it.forProject) it.name + " (${projectTitle})" else it.name }
    return result
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

  private fun getProject(): Project? {
    return CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(WindowManager.getInstance().mostRecentFocusedWindow))
  }

  private fun getValues(manager: PropertiesComponent, list: ArrayList<DoNotAskInfo>, forProject: Boolean) {
    if (manager is BasePropertyService) {
      manager.forEachPrimitiveValue { key, value ->
        if (key.startsWith(DO_NOT_ASK_KEY_PREFIX)) {
          list.add(DoNotAskInfo(key.substring(DO_NOT_ASK_KEY_PREFIX.length), value, forProject))
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