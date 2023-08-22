// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.MODULE_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.tree.DefaultTreeModel

private val PREDEFINED_PRIORITIES = mapOf(DIRECTORY_GROUPING to 10, MODULE_GROUPING to 20, REPOSITORY_GROUPING to 30)

open class ChangesGroupingSupport(val project: Project, source: Any, val showConflictsNode: Boolean) {
  private val changeSupport = PropertyChangeSupport(source)
  private val _groupingKeys = ConcurrentCollectionFactory.createConcurrentSet<String>() // updated on EDT
  val groupingKeys get() = _groupingKeys.toSet()

  operator fun get(groupingKey: @NonNls String): Boolean {
    if (!isAvailable(groupingKey)) return false

    return _groupingKeys.contains(groupingKey)
  }

  @RequiresEdt
  operator fun set(groupingKey: String, state: Boolean) {
    if (!isAvailable(groupingKey)) throw IllegalArgumentException("Unknown grouping $groupingKey") // NON-NLS

    val currentState = _groupingKeys.contains(groupingKey)
    if (currentState == state) return

    val oldGroupingKeys = _groupingKeys.toSet()
    if (state) {
      _groupingKeys += groupingKey
    }
    else {
      _groupingKeys -= groupingKey
    }
    changeSupport.firePropertyChange(PROP_GROUPING_KEYS, oldGroupingKeys, _groupingKeys.toSet())
  }

  val grouping: ChangesGroupingPolicyFactory get() = CombinedGroupingPolicyFactory()
  val isNone: Boolean get() = _groupingKeys.isEmpty()
  val isDirectory: Boolean get() = this[DIRECTORY_GROUPING]

  @RequiresEdt
  fun setGroupingKeysOrSkip(newGroupingKeys: Collection<String>) {
    _groupingKeys.clear()
    _groupingKeys += newGroupingKeys.filter { groupingKey -> isAvailable(groupingKey) }
  }

  open fun isAvailable(groupingKey: String) = findFactory(groupingKey) != null

  fun addPropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.addPropertyChangeListener(listener)
  }

  fun removePropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.removePropertyChangeListener(listener)
  }

  private inner class CombinedGroupingPolicyFactory : ChangesGroupingPolicyFactory() {
    override fun createGroupingPolicy(project: Project, model: DefaultTreeModel): ChangesGroupingPolicy {
      var result = DefaultChangesGroupingPolicy.Factory(showConflictsNode).createGroupingPolicy(project, model)
      _groupingKeys.sortedByDescending { PREDEFINED_PRIORITIES[it] }.forEach { groupingKey ->
        val factory = findFactory(groupingKey) ?: throw IllegalArgumentException("Unknown grouping $groupingKey") // NON-NLS
        result = factory.createGroupingPolicy(project, model).apply { setNextGroupingPolicy(result) }
      }
      return result
    }
  }

  class Disabled(project: Project, source: Any) : ChangesGroupingSupport(project, source, false) {
    override fun isAvailable(groupingKey: String): Boolean = false
  }

  companion object {
    @JvmField
    val KEY = DataKey.create<ChangesGroupingSupport>("ChangesTree.GroupingSupport")

    const val PROP_GROUPING_KEYS = "ChangesGroupingKeys" // NON-NLS
    const val DIRECTORY_GROUPING = "directory" // NON-NLS
    const val MODULE_GROUPING = "module" // NON-NLS
    const val REPOSITORY_GROUPING = "repository" // NON-NLS
    const val NONE_GROUPING = "none" // NON-NLS

    private val FACTORIES = ClearableLazyValue.create { buildFactories() }

    init {
      ChangesGroupingPolicyFactory.EP_NAME.addChangeListener({ FACTORIES.drop() }, null)
    }

    @JvmStatic
    fun getFactory(key: String): ChangesGroupingPolicyFactory {
      return findFactory(key) ?: NoneChangesGroupingFactory
    }

    @JvmStatic
    fun findFactory(key: String): ChangesGroupingPolicyFactory? {
      return FACTORIES.value[key]
    }

    private fun buildFactories(): Map<String, ChangesGroupingPolicyFactory> {
      val result = mutableMapOf<String, ChangesGroupingPolicyFactory>()
      for (bean in ChangesGroupingPolicyFactory.EP_NAME.extensionList) {
        val key = bean.key ?: continue
        val clazz = bean.implementationClass ?: continue
        try {
          result[key] = ApplicationManager.getApplication().instantiateClass(clazz, bean.pluginDescriptor)
        }
        catch (e: Throwable) {
          logger<ChangesGroupingSupport>().error(e)
        }
      }
      return result
    }
  }
}