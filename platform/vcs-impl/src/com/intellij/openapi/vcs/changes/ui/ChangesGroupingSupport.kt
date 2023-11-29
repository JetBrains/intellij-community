// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.tree.DefaultTreeModel

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
      sortedFactoriesFor(_groupingKeys).forEach { factory ->
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

    private val FACTORIES: ClearableLazyValue<AvailableFactories> = ClearableLazyValue.create { buildFactories() }

    init {
      ChangesGroupingPolicyFactory.EP_NAME.addChangeListener({ FACTORIES.drop() }, null)
    }

    @JvmStatic
    fun getFactory(key: String): ChangesGroupingPolicyFactory {
      return findFactory(key) ?: NoneChangesGroupingFactory
    }

    @JvmStatic
    fun findFactory(key: String): ChangesGroupingPolicyFactory? {
      val availableFactories = FACTORIES.value
      return availableFactories.keyToFactory[key]
    }

    private fun sortedFactoriesFor(keys: Collection<String>): List<ChangesGroupingPolicyFactory> {
      val availableFactories = FACTORIES.value
      return keys
        .sortedByDescending { availableFactories.keyToWeight[it] ?: ChangesGroupingPolicyFactoryEPBean.DEFAULT_WEIGHT }
        .map { groupingKey ->
          availableFactories.keyToFactory[groupingKey] ?: throw IllegalArgumentException("Unknown grouping: $groupingKey") // NON-NLS
        }
    }

    private fun buildFactories(): AvailableFactories {
      val keyToFactory = mutableMapOf<String, ChangesGroupingPolicyFactory>()
      val keyToWeight = mutableMapOf<String, Int>()

      for (bean in ChangesGroupingPolicyFactory.EP_NAME.extensionList) {
        val key = bean.key ?: continue
        try {
          keyToFactory[key] = bean.getInstance()
          keyToWeight[key] = bean.weight
        }
        catch (e: Throwable) {
          logger<ChangesGroupingSupport>().error(e)
        }
      }
      return AvailableFactories(keyToFactory, keyToWeight)
    }

    private class AvailableFactories(
      val keyToFactory: Map<String, ChangesGroupingPolicyFactory>,
      val keyToWeight: Map<String, Int>
    )
  }
}
