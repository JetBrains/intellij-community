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

  @JvmField val pluginToId: MutableMap<PluginId, MutableList<String>> = HashMap()
  @JvmField var registeredActionCount: Int = 0

  @JvmField val baseActions: MutableMap<String, AnAction> = HashMap()

  @JvmField val lock: Any = Any()

  fun getGroupIdListById(groupId: String): List<String> = idToDescriptor.get(groupId)?.groupIds ?: java.util.List.of()
}

internal data class ActionManagerStateActionItemDescriptor(
  @JvmField var index: Int = -1,
) {
  @JvmField var groupIds: List<String> = java.util.List.of()

  fun addGroupMapping(groupId: String) {
    groupIds = when {
      groupIds.isEmpty() -> java.util.List.of(groupId)
      groupIds.size == 1 -> java.util.List.of(groupIds.get(0), groupId)
      else -> groupIds + groupId
    }
  }

  fun removeGroupMapping(groupId: String) {
    groupIds -= groupId
  }
}