// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplacePutWithAssignment")

package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.PluginId

internal class ActionManagerState {
  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  @Volatile
  private var prohibitedActionIds: Set<String> = java.util.Set.of()

  private val actionToId: MutableMap<Any, String> = HashMap(5_000, 0.5f)

  private val registrationData: MutableMap<String, ActionManagerRegistrationData> = HashMap()

  private val groupMappings: MutableMap<String, List<String>> = HashMap()

  private var registeredActionCount: Int = 0

  private val baseActions: MutableMap<String, AnAction> = HashMap()

  val lock: Any = Any()

  fun isActionProhibited(actionId: String): Boolean = prohibitedActionIds.contains(actionId)

  fun prohibitAction(actionId: String) {
    synchronized(lock) {
      prohibitedActionIds = HashSet(prohibitedActionIds).let {
        it.add(actionId)
        it
      }
    }
  }

  fun resetProhibitedActions() {
    synchronized(lock) {
      prohibitedActionIds = java.util.Set.of()
    }
  }

  fun getActionId(action: Any): String? {
    synchronized(lock) {
      return actionToId.get(action)
    }
  }

  fun putActionId(action: Any, actionId: String): String? {
    synchronized(lock) {
      return actionToId.putIfAbsent(action, actionId)
    }
  }

  fun setActionId(action: Any, actionId: String) {
    synchronized(lock) {
      actionToId.put(action, actionId)
    }
  }

  fun removeActionId(action: Any): String? {
    synchronized(lock) {
      return actionToId.remove(action)
    }
  }

  fun getBaseAction(actionId: String): AnAction? {
    synchronized(lock) {
      return baseActions.get(actionId)
    }
  }

  fun putBaseAction(actionId: String, action: AnAction) {
    synchronized(lock) {
      baseActions.put(actionId, action)
    }
  }

  fun removeBaseAction(actionId: String): AnAction? {
    synchronized(lock) {
      return baseActions.remove(actionId)
    }
  }

  fun getPluginId(actionId: String): PluginId? {
    synchronized(lock) {
      return registrationData.get(actionId)?.pluginId
    }
  }

  fun getRegistrationIndex(actionId: String): Int {
    synchronized(lock) {
      return registrationData.get(actionId)?.index ?: -1
    }
  }

  fun registerAction(actionId: String, pluginId: PluginId?, oldIndex: Int, oldGroups: List<String>?) {
    synchronized(lock) {
      val data = registrationData.computeIfAbsent(actionId) { ActionManagerRegistrationData() }
      data.index = if (oldIndex >= 0) oldIndex else registeredActionCount++
      if (pluginId != null) {
        data.pluginId = pluginId
      }
      if (oldGroups != null) {
        if (oldGroups.isEmpty()) {
          groupMappings.remove(actionId)
        }
        else {
          groupMappings.put(actionId, oldGroups)
        }
      }
    }
  }

  fun removeAction(actionId: String): List<String> {
    synchronized(lock) {
      registrationData.remove(actionId)
      return groupMappings.remove(actionId) ?: java.util.List.of()
    }
  }

  fun registrationOrderSnapshot(): HashMap<String, Int> {
    synchronized(lock) {
      val result = HashMap<String, Int>(registrationData.size)
      for ((id, data) in registrationData) {
        result.put(id, data.index)
      }
      return result
    }
  }

  fun getParentGroupIds(id: String): List<String> {
    synchronized(lock) {
      return groupMappings.get(id) ?: java.util.List.of()
    }
  }

  fun getPluginActions(pluginId: PluginId): List<String> {
    synchronized(lock) {
      return registrationData.asSequence()
        .filter { it.value.pluginId == pluginId }
        .map { it.key }
        .toList()
    }
  }

  fun addGroupMapping(actionId: String, groupId: String) {
    synchronized(lock) {
      groupMappings.put(actionId, addGroupMapping(groupMappings.get(actionId) ?: java.util.List.of(), groupId))
    }
  }

  fun removeGroupMapping(actionId: String, groupId: String) {
    synchronized(lock) {
      val groupIds = groupMappings.get(actionId) ?: return
      val result = removeGroupMapping(groupIds, groupId)
      if (result.isEmpty()) {
        groupMappings.remove(actionId)
      }
      else if (result !== groupIds) {
        groupMappings.put(actionId, result)
      }
    }
  }

  fun removeGroupMappingFromAll(groupId: String) {
    synchronized(lock) {
      val iterator = groupMappings.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val result = removeGroupMapping(entry.value, groupId)
        if (result.isEmpty()) {
          iterator.remove()
        }
        else if (result !== entry.value) {
          entry.setValue(result)
        }
      }
    }
  }
}

private data class ActionManagerRegistrationData(
  @JvmField var index: Int = -1,
) {
  @JvmField
  var pluginId: PluginId? = null
}

private fun addGroupMapping(groupIds: List<String>, groupId: String): List<String> {
  val size = groupIds.size
  return when (size) {
    0 -> java.util.List.of(groupId)
    1 -> java.util.List.of(groupIds.get(0), groupId)
    2 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupId)
    3 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupIds.get(2), groupId)
    4 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupIds.get(2), groupIds.get(3), groupId)
    else -> groupIds + groupId
  }
}

private fun removeGroupMapping(groupIds: List<String>, groupId: String): List<String> {
  val index = groupIds.indexOf(groupId)
  if (index < 0) {
    return groupIds
  }

  if (groupIds.size <= 1) {
    return java.util.List.of()
  }

  val list = ArrayList(groupIds)
  list.removeAt(index)
  return list
}