// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
internal class FrontendXBreakpointTypesManager(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  private val types = ConcurrentHashMap<XBreakpointTypeId, XBreakpointTypeProxy>()
  private val typesChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val typesChangedWithReplay = typesChanged.shareIn(cs, SharingStarted.Eagerly, replay = 1)
  private val typesInitialized = MutableStateFlow(false)

  init {
    cs.launch {
      durableWithStateReset(block = {
        val (initialBreakpointTypes, breakpointTypesFlow) = XBreakpointTypeApi.getInstance().getBreakpointTypeList(project.projectId())
        handleBreakpointTypesFromBackend(cs, initialBreakpointTypes)
        typesInitialized.value = true
        breakpointTypesFlow.toFlow().collectLatest {
          handleBreakpointTypesFromBackend(cs, it)
          typesChanged.tryEmit(Unit)
        }
      }, stateReset = {
        types.clear()
        typesInitialized.value = false
      })
    }
  }

  private fun handleBreakpointTypesFromBackend(cs: CoroutineScope, breakpointTypes: List<XBreakpointTypeDto>) {
    for (dto in breakpointTypes) {
      types.putIfAbsent(dto.id, createFrontendXBreakpointType(project, cs, dto))
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

  private suspend fun awaitTypesInitialized() {
    typesInitialized.first { it }
  }

  suspend fun findTypeById(id: XBreakpointTypeId): XBreakpointTypeProxy? {
    types[id]?.let { return it }
    awaitTypesInitialized()
    return findOrAwaitElement(
      updateFlow = typesChangedWithReplay,
      logMessage = "breakpoint type ${id.id}",
      timeoutS = 60,
    ) {
      types[id]?.let { Ref.create(it) }
    }
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
