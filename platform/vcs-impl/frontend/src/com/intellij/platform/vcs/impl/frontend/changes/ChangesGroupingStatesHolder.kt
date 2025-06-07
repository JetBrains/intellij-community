// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rhizome.GroupingItemEntity
import com.intellij.platform.vcs.impl.shared.rhizome.GroupingItemsEntity
import fleet.kernel.rete.*
import fleet.kernel.rete.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ChangesGroupingStatesHolder(private val cs: CoroutineScope) {
  private val states: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
  private val _allGroupingKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  val allGroupingKeys: Set<String> = _allGroupingKeys

  init {
    initializeGroupingKeys()
    subscribeToGroupingItemsChanges()
  }

  private fun initializeGroupingKeys() {
    cs.launch {
      GroupingItemEntity.each().tokensFlow().collect { (added, groupingItem) ->
        val name = groupingItem.value.name
        if (added) {
          _allGroupingKeys.add(name)
        }
        else {
          _allGroupingKeys.remove(name)
        }
      }
    }
  }

  private fun subscribeToGroupingItemsChanges() {
    cs.launch {
      GroupingItemsEntity.each().collect { itemsEntity ->
        val itemsList = itemsEntity.items.map { it.name }.toMutableSet()
        states[itemsEntity.place] = itemsList
        itemsEntity.asQuery()[GroupingItemsEntity.Items].collect { groupingKey ->
          itemsList.add(groupingKey.name)
        }
      }
    }
  }

  fun isGroupingEnabled(place: String, groupingKey: String): Boolean {
    return states[place]?.contains(groupingKey) == true
  }

  fun setGroupingEnabled(place: String, groupingKey: String, isEnabled: Boolean) {
    val set = states.computeIfAbsent(place) { mutableSetOf() }
    if (isEnabled) {
      set.add(groupingKey)
    }
    else {
      set.remove(groupingKey)
    }
  }

  fun getGroupingsForPlace(place: String): Set<String> {
    return states[place]?.toSet() ?: emptySet()
  }

  companion object {
    fun getInstance(project: Project): ChangesGroupingStatesHolder = project.service()
  }
}