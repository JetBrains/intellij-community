// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeDto
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.rpc.client.durable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@Service(Service.Level.PROJECT)
internal class FrontendXBreakpointTypesManager(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  private val types = ConcurrentHashMap<XBreakpointTypeId, XBreakpointTypeProxy>()
  private val typesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val typesInitialized = CompletableDeferred<Unit>()

  init {
    cs.launch {
      val (initialBreakpointTypes, breakpointTypesFlow) = durable {
        XBreakpointTypeApi.getInstance().getBreakpointTypeList(project.projectId())
      }
      handleBreakpointTypesFromBackend(initialBreakpointTypes)
      typesInitialized.complete(Unit)

      breakpointTypesFlow.toFlow().collectLatest {
        handleBreakpointTypesFromBackend(it)
        typesChanged.tryEmit(Unit)
      }
    }
  }

  private fun handleBreakpointTypesFromBackend(breakpointTypes: List<XBreakpointTypeDto>) {
    for (dto in breakpointTypes) {
      types.putIfAbsent(dto.id, createFrontendXBreakpointType(project, dto))
    }
    val typesToRemove = types.keys - breakpointTypes.map { it.id }.toSet()
    for (typeToRemove in typesToRemove) {
      types.remove(typeToRemove)
    }
  }

  fun subscribeOnBreakpointTypesChanges(scope: CoroutineScope, action: suspend () -> Unit) {
    scope.launch {
      typesChanged.collectLatest {
        action()
      }
    }
  }

  fun typesInitialized(): Deferred<Unit> {
    return typesInitialized
  }

  fun typesChangedFlow(): Flow<Unit> = typesChanged

  fun getTypeById(id: XBreakpointTypeId): XBreakpointTypeProxy? {
    return types[id]
  }

  fun getBreakpointTypes(): List<XBreakpointTypeProxy> {
    return types.values.toList()
  }

  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy> {
    return types.values.filterIsInstance<XLineBreakpointTypeProxy>()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXBreakpointTypesManager = project.service<FrontendXBreakpointTypesManager>()
  }
}