// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionFactory
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.MODULE_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.tree.DefaultTreeModel

private val PREDEFINED_PRIORITIES = mapOf(DIRECTORY_GROUPING to 10, MODULE_GROUPING to 20, REPOSITORY_GROUPING to 30)

open class ChangesGroupingSupport(val project: Project, source: Any, val showConflictsNode: Boolean) {
  private val changeSupport = PropertyChangeSupport(source)
  private val groupingConfig: MutableMap<String, Boolean>

  val groupingKeys: Set<String>
    get() = groupingConfig.filterValues { it }.keys

  init {
    groupingConfig = HashMap()
    for (epBean in ChangesGroupingPolicyFactory.EP_NAME.extensionList) {
      if (epBean.key != null) {
        groupingConfig.put(epBean.key, false)
      }
    }
  }

  operator fun get(groupingKey: @NonNls String): Boolean {
    if (!isAvailable(groupingKey)) throw IllegalArgumentException("Unknown grouping $groupingKey") // NON-NLS

    return groupingConfig[groupingKey]!!
  }

  operator fun set(groupingKey: String, state: Boolean) {
    if (!isAvailable(groupingKey)) throw IllegalArgumentException("Unknown grouping $groupingKey") // NON-NLS

    if (groupingConfig[groupingKey] != state) {
      val oldGroupingKeys = groupingKeys

      groupingConfig[groupingKey] = state
      changeSupport.firePropertyChange(PROP_GROUPING_KEYS, oldGroupingKeys, groupingKeys)
    }
  }

  val grouping: ChangesGroupingPolicyFactory
    get() = object : ChangesGroupingPolicyFactory() {
      override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy {
        var result = DefaultChangesGroupingPolicy.Factory(showConflictsNode).createGroupingPolicy(project, model)
        groupingConfig.filterValues { it }.keys.sortedByDescending { PREDEFINED_PRIORITIES[it] }.forEach {
          result = findFactory(it)!!.createGroupingPolicy(project, model).apply { setNextGroupingPolicy(result) }
        }
        return result
      }
    }

  val isNone: Boolean get() = groupingKeys.isEmpty()
  val isDirectory: Boolean get() = this[DIRECTORY_GROUPING]

  fun setGroupingKeysOrSkip(groupingKeys: Set<String>) {
    groupingConfig.entries.forEach { it.setValue(it.key in groupingKeys) }
  }
  open fun isAvailable(groupingKey: String) = findFactory(groupingKey) != null

  fun addPropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.addPropertyChangeListener(listener)
  }

  fun removePropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.removePropertyChangeListener(listener)
  }

  companion object {
    @JvmField
    val KEY = DataKey.create<ChangesGroupingSupport>("ChangesTree.GroupingSupport")

    const val PROP_GROUPING_KEYS = "ChangesGroupingKeys" // NON-NLS
    const val DIRECTORY_GROUPING = "directory" // NON-NLS
    const val MODULE_GROUPING = "module" // NON-NLS
    const val REPOSITORY_GROUPING = "repository" // NON-NLS
    const val NONE_GROUPING = "none" // NON-NLS

    @JvmStatic
    fun getFactory(key: String): ChangesGroupingPolicyFactory {
      return findFactory(key) ?: NoneChangesGroupingFactory
    }

    private fun findFactory(key: String): ChangesGroupingPolicyFactory? {
      return KeyedExtensionFactory.findByKey(key, ChangesGroupingPolicyFactory.EP_NAME, ApplicationManager.getApplication())
    }
  }
}