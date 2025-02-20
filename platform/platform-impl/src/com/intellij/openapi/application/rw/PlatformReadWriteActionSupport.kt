// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.rw

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ObjectUtils

internal class PlatformReadWriteActionSupport : ReadWriteActionSupport {

  private val retryMarker: Any = ObjectUtils.sentinel("rw action")
  
  init {
    // init the write action counter listener
    ApplicationManager.getApplication().service<AsyncExecutionService>() 
  }
  
  override fun smartModeConstraint(project: Project): ReadConstraint {
    check(!LightEdit.owns(project)) {
      "ReadConstraint.inSmartMode() can't be used in LightEdit mode, check that LightEdit.owns(project)==false before calling"
    }
    return SmartModeReadConstraint(project)
  }

  override fun committedDocumentsConstraint(project: Project): ReadConstraint {
    return CommittedDocumentsConstraint(project)
  }

  override suspend fun <X> executeReadAction(
    constraints: List<ReadConstraint>,
    undispatched: Boolean,
    blocking: Boolean,
    action: () -> X,
  ): X {
    return InternalReadAction(constraints, undispatched, blocking, action).runReadAction()
  }

  override fun <X, E : Throwable> computeCancellable(action: ThrowableComputable<X, E>): X {
    return cancellableReadAction {
      action.compute()
    }
  }

  override suspend fun <X> executeReadAndWriteAction(
    constraints: Array<out ReadConstraint>,
    action: ReadAndWriteScope.() -> ReadResult<X>,
  ): X {
    while (true) {
      val (readResult: ReadResult<X>, stamp: Long) = constrainedReadAction(*constraints) {
        Pair(ReadResult.Companion.action(), AsyncExecutionServiceImpl.getWriteActionCounter())
      }
      when (readResult) {
        is ReadResult.Value -> {
          return readResult.value
        }
        is ReadResult.WriteAction -> {
          val writeResult = edtWriteAction {
            // Start of this Write Action increase count of write actions by one
            val writeStamp = AsyncExecutionServiceImpl.getWriteActionCounter() - 1
            if (stamp == writeStamp) {
              readResult.action()
            }
            else {
              retryMarker
            }
          }
          if (writeResult !== retryMarker) {
            @Suppress("UNCHECKED_CAST")
            return writeResult as X
          }
        }
      }
    }
  }
}
