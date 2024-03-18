// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.PluginId

internal class ActionManagerState {
  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  @Volatile
  @JvmField var prohibitedActionIds: MutableSet<String> = java.util.Set.of()

  @JvmField val actionToId: MutableMap<Any, String> = HashMap(5_000, 0.5f)

  @JvmField val idToDescriptor: MutableMap<String, ActionManagerStateActionItemDescriptor> = HashMap()

  @JvmField var registeredActionCount: Int = 0

  @JvmField val baseActions: MutableMap<String, AnAction> = HashMap()

  @JvmField val lock: Any = Any()

  fun getParentGroupIds(id: String): List<String> {
    synchronized(lock) {
      return idToDescriptor.get(id)?.groupIds ?: java.util.List.of()
    }
  }

  fun getPluginActions(pluginId: PluginId): List<String> {
    synchronized(lock) {
      return idToDescriptor.asSequence()
        .filter { it.value.pluginId == pluginId }
        .map { it.key }
        .toList()
    }
  }

  fun removeGroupMapping(actionId: String, groupId: String) {
    idToDescriptor.get(actionId)?.removeGroupMapping(groupId)
  }
}

internal data class ActionManagerStateActionItemDescriptor(
  @JvmField var index: Int = -1,
) {
  @JvmField var pluginId: PluginId? = null
  @JvmField var groupIds: List<String> = java.util.List.of()

  fun addGroupMapping(groupId: String) {
    val groupIds = groupIds
    val size = groupIds.size
    this.groupIds = when (size) {
      0 -> java.util.List.of(groupId)
      1 -> java.util.List.of(groupIds.get(0), groupId)
      2 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupId)
      3 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupIds.get(2), groupId)
      4 -> java.util.List.of(groupIds.get(0), groupIds.get(1), groupIds.get(2), groupIds.get(3), groupId)
      else -> groupIds + groupId
    }
  }

  fun removeGroupMapping(groupId: String) {
    val groupIds = groupIds
    val index = groupIds.indexOf(groupId)
    if (index < 0) {
      return
    }

    if (groupIds.size <= 1) {
      this.groupIds = java.util.List.of()
    }
    else {
      val list = ArrayList(groupIds)
      list.removeAt(index)
      this.groupIds = list
    }
  }
}