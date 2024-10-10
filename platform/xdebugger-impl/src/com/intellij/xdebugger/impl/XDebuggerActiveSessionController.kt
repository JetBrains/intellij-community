// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asEntity
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.jetbrains.rhizomedb.*
import fleet.kernel.DurableEntityType
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.jetbrains.annotations.ApiStatus

private class XDebuggerActiveSessionEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      XDebuggerActiveSessionEntity,
      LocalXDebuggerSessionEvaluatorEntity
    )
  }
}

@ApiStatus.Internal
data class XDebuggerActiveSessionEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val evaluatorId by EvaluatorId

  @ApiStatus.Internal
  companion object : DurableEntityType<XDebuggerActiveSessionEntity>(
    XDebuggerActiveSessionEntity::class.java.name,
    "com.intellij",
    ::XDebuggerActiveSessionEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project", RefFlags.UNIQUE)
    var EvaluatorId = optionalValue<XDebuggerEvaluatorId>("evaluatorId", XDebuggerEvaluatorId.serializer())
  }
}

@ApiStatus.Internal
data class LocalXDebuggerSessionEvaluatorEntity(override val eid: EID) : Entity {
  val sessionEntity by Session
  val evaluator by Evaluator

  @ApiStatus.Internal
  companion object : DurableEntityType<LocalXDebuggerSessionEvaluatorEntity>(
    LocalXDebuggerSessionEvaluatorEntity::class.java.name,
    "com.intellij",
    ::LocalXDebuggerSessionEvaluatorEntity
  ) {
    val Session = requiredRef<XDebuggerActiveSessionEntity>("session", RefFlags.UNIQUE)
    val Evaluator = requiredTransient<XDebuggerEvaluator>("evaluator")
  }
}

internal fun synchronizeActiveSessionWithDb(coroutineScope: CoroutineScope, project: Project, activeSessionState: Flow<XDebugSessionImpl?>) {
  coroutineScope.launch(Dispatchers.IO) {
    activeSessionState.collectLatest { newActiveSession ->
      // if there is no active session -> remove entity from DB
      if (newActiveSession == null) {
        withKernel {
          change {
            shared {
              val projectEntity = project.asEntity()
              entity(XDebuggerActiveSessionEntity.Project, projectEntity)?.delete()
            }
          }
        }
        return@collectLatest
      }
      // remove an old session and create a new one
      val newActiveSessionEntity = withKernel {
        withKernel {
          change {
            shared {
              val projectEntity = project.asEntity()
              entity(XDebuggerActiveSessionEntity.Project, projectEntity)?.delete()
              XDebuggerActiveSessionEntity.new {
                it[XDebuggerActiveSessionEntity.Project] = projectEntity
              }
            }
          }
        }
      }
      // synchronize evaluator for the created session entity
      // this scope will be cancelled when a newActiveSession is appeared
      supervisorScope {
        launch {
          synchronizeSessionEvaluatorWithDb(newActiveSession, newActiveSessionEntity)
        }
      }
    }
  }
}

private suspend fun synchronizeSessionEvaluatorWithDb(session: XDebugSessionImpl, sessionEntity: XDebuggerActiveSessionEntity) {
  // NB!: we assume that the current evaluator depends only on the current StackFrame
  session.currentStackFrameFlow.collect { stackFrameRef ->
    val stackFrame = stackFrameRef.get()
    withKernel {
      change {
        val currentEvaluator = stackFrame?.evaluator
        if (currentEvaluator == null) {
          entity(LocalXDebuggerSessionEvaluatorEntity.Session, sessionEntity)?.delete()
          shared {
            sessionEntity[XDebuggerActiveSessionEntity.EvaluatorId] = null
          }
        }
        else {
          val evaluatorEntity = LocalXDebuggerSessionEvaluatorEntity.upsert(LocalXDebuggerSessionEvaluatorEntity.Session, sessionEntity) {
            it[LocalXDebuggerSessionEvaluatorEntity.Session] = sessionEntity
            it[LocalXDebuggerSessionEvaluatorEntity.Evaluator] = currentEvaluator
          }
          val evaluatorId = XDebuggerEvaluatorId(evaluatorEntity.eid)
          shared {
            sessionEntity[XDebuggerActiveSessionEntity.EvaluatorId] = evaluatorId
          }
        }
      }
    }
  }
}