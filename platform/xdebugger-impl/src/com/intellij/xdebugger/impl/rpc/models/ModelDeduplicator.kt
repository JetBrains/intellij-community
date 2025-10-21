// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.impl.util.identityConcurrentHashMap
import com.intellij.xdebugger.impl.util.identityWrapper
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

internal open class ModelDeduplicator<Entity, EntityModel> {
  private val scopesMap = ConcurrentHashMap<CoroutineScope, ScopeBoundStorage<Entity, EntityModel>>()

  @OptIn(AwaitCancellationAndInvoke::class)
  fun getOrCreateModel(coroutineScope: CoroutineScope, entity: Entity, createModel: () -> EntityModel) = scopesMap.computeIfAbsent(coroutineScope) {
    ScopeBoundStorage<Entity, EntityModel>().also { scopeBoundStorage ->
      coroutineScope.awaitCancellationAndInvoke {
        // Note that it's still up to BackendGlobalIdsManager to remove global IDs once their relevant coroutine scopes are canceled
        scopesMap.remove(coroutineScope)
        scopeBoundStorage.clear()
      }
    }
  }.getOrStore(entity, createModel)

}

private class ScopeBoundStorage<Entity, EntityModel> {
  private val groupToModelMap = identityConcurrentHashMap<Entity, EntityModel>()

  fun getOrStore(value: Entity, createModel: () -> EntityModel): EntityModel {
    return groupToModelMap.computeIfAbsent(value.identityWrapper()) { createModel() }
  }

  fun clear() {
    groupToModelMap.clear()
  }
}
