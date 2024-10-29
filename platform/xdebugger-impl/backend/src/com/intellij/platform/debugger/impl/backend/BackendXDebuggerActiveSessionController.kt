// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerActiveSessionEntity
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private class BackendXDebuggerActiveSessionControllerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<BackendXDebuggerActiveSessionController>().synchronizeActiveSessionWithDb()
  }
}

@Service(Service.Level.PROJECT)
private class BackendXDebuggerActiveSessionController(private val project: Project, private val cs: CoroutineScope) {
  fun synchronizeActiveSessionWithDb() {
    val activeSessionState = (XDebuggerManager.getInstance(project) as? XDebuggerManagerImpl)?.currentSessionFlow ?: return
    cs.launch(Dispatchers.IO) {
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
}