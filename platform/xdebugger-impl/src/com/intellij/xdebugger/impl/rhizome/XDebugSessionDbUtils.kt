// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rhizome

import com.intellij.platform.project.asEntity
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.kernel.change
import fleet.kernel.withEntities
import fleet.util.UID
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * This function handles creation and disposing of [XDebugSessionEntity],
 * so [XDebugSessionEntity] is attached to the [session] lifetime.
 *
 * This code should be located in XDebugSessionImpl, but it is written separately since suspend functions cannot be called there.
 */
internal fun storeXDebugSessionInDb(sessionScope: CoroutineScope, session: XDebugSessionImpl): Deferred<XDebugSessionEntity> {
  val sessionEntity = AtomicReference<XDebugSessionEntity?>()
  val deferred = sessionScope.async {
    val projectEntity = session.project.asEntity()
    val createdSessionEntity = withEntities(projectEntity) {
        change {
          XDebugSessionEntity.new {
            it[XDebugSessionEntity.ProjectEntity] = projectEntity
            it[XDebugSessionEntity.SessionId] = XDebugSessionId(UID.random())
            it[XDebugSessionEntity.Session] = session
          }
        }
      }
    sessionEntity.set(createdSessionEntity)
    createdSessionEntity
  }

  // delete session entity when session coroutine scope is disposed
  sessionScope.launch {
    try {
      awaitCancellation()
    }
    finally {
      withContext(NonCancellable) {
        val entity = sessionEntity.get() ?: return@withContext
        change {
          entity.delete()
        }
      }
    }
  }

  return deferred
}