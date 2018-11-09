// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.KeyedExtensionFactory
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.MODULE_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.tree.DefaultTreeModel

private val PREDEFINED_PRIORITIES = mapOf(DIRECTORY_GROUPING to 10, MODULE_GROUPING to 20, REPOSITORY_GROUPING to 30)

class ChangesGroupingSupport(val project: Project, source: Any, val showConflictsNode: Boolean) {
  private val changeSupport = PropertyChangeSupport(source)
  private val groupingFactories = collectFactories(project)
  private val groupingConfig = groupingFactories.allKeys.associateBy({ it }, { false }).toMutableMap()

  val groupingKeys: Set<String> get() = groupingConfig.filterValues { it }.keys

  operator fun get(groupingKey: String): Boolean {
    if (!isAvailable(groupingKey)) throw IllegalArgumentException("Unknown grouping $groupingKey")

    return groupingConfig[groupingKey]!!
  }

  operator fun set(groupingKey: String, state: Boolean) {
    if (!isAvailable(groupingKey)) throw IllegalArgumentException("Unknown grouping $groupingKey")

    if (groupingConfig[groupingKey] != state) {
      val oldGroupingKeys = groupingKeys

      groupingConfig[groupingKey] = state
      changeSupport.firePropertyChange(PROP_GROUPING_KEYS, oldGroupingKeys, groupingKeys)
    }
  }

  val grouping: ChangesGroupingPolicyFactory
    get() = object : ChangesGroupingPolicyFactory() {
      override fun createGroupingPolicy(model: DefaultTreeModel): ChangesGroupingPolicy {
        var result: ChangesGroupingPolicy = DefaultChangesGroupingPolicy.Factory(project, showConflictsNode).createGroupingPolicy(model)
        groupingConfig.filterValues { it }.keys.sortedByDescending { PREDEFINED_PRIORITIES[it] }.forEach {
          result = groupingFactories.getByKey(it)!!.createGroupingPolicy(model).apply { setNextGroupingPolicy(result) }
        }
        return result
      }
    }

  val isNone: Boolean get() = groupingKeys.isEmpty()
  val isDirectory: Boolean get() = this[DIRECTORY_GROUPING]

  fun setGroupingKeysOrSkip(groupingKeys: Set<String>) {
    groupingConfig.entries.forEach { it.setValue(it.key in groupingKeys) }
  }
  fun isAvailable(groupingKey: String): Boolean = groupingFactories.getByKey(groupingKey) != null

  fun addPropertyChangeListener(listener: PropertyChangeListener): Unit = changeSupport.addPropertyChangeListener(listener)
  fun removePropertyChangeListener(listener: PropertyChangeListener): Unit = changeSupport.removePropertyChangeListener(listener)

  companion object {
    @JvmField val KEY: DataKey<ChangesGroupingSupport> = DataKey.create<ChangesGroupingSupport>("ChangesTree.GroupingSupport")
    const val PROP_GROUPING_KEYS: String = "ChangesGroupingKeys"
    const val DIRECTORY_GROUPING: String = "directory"
    const val MODULE_GROUPING: String = "module"
    const val REPOSITORY_GROUPING: String = "repository"
    const val NONE_GROUPING: String = "none"

    private fun collectFactories(project: Project): KeyedExtensionFactory<ChangesGroupingPolicyFactory, String> {
      return object : KeyedExtensionFactory<ChangesGroupingPolicyFactory, String>(
        ChangesGroupingPolicyFactory::class.java,
        ChangesGroupingPolicyFactory.EP_NAME,
        project.picoContainer
      ) {
        override fun getKey(key: String) = key
      }
    }

    @JvmStatic
    fun getFactory(project: Project, key: String): ChangesGroupingPolicyFactory {
      return collectFactories(project).getByKey(key) ?: NoneChangesGroupingFactory
    }
  }
}