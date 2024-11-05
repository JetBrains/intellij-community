// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.impl.shared.rhizome.GroupingItemEntity
import com.intellij.vcs.impl.shared.rhizome.GroupingItemsEntity
import fleet.kernel.onDispose
import fleet.kernel.rete.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ChangesGroupingStatesHolder(private val project: Project, private val cs: CoroutineScope) {
  private val states: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
  val allGroupingKeys: MutableSet<String> = mutableSetOf()

  init {
    initializeGroupingKeys()
    subscribeToGroupingItemsChanges()
  }

  private fun initializeGroupingKeys() {
    cs.launch {
      withKernel {
        GroupingItemEntity.each().collect {
          allGroupingKeys.add(it.name)
          it.onDispose(coroutineContext[Rete]!!) {
            allGroupingKeys.remove(it.name)
          }
        }
      }
    }
  }

  private fun subscribeToGroupingItemsChanges() {
    cs.launch {
      withKernel {
        GroupingItemsEntity.each().collect { itemsEntity ->
          val itemsList = itemsEntity.items.map { it.name }.toMutableSet()
          states[itemsEntity.place] = itemsList
          itemsEntity.asQuery()[GroupingItemsEntity.Items].collect { groupingKey ->
            itemsList.add(groupingKey.name)
          }
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