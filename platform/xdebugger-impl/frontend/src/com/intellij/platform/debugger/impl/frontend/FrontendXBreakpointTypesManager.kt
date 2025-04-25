// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeApi
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeDto
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface FrontendXBreakpointTypesManager {
  fun getTypeById(id: XBreakpointTypeId): XBreakpointTypeProxy?
  fun getBreakpointTypes(): List<XBreakpointTypeProxy>
  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy>

  companion object {
    fun getInstance(project: Project): FrontendXBreakpointTypesManager = project.service<FrontendXBreakpointTypesManagerService>()

    suspend fun getInstanceSuspending(project: Project): FrontendXBreakpointTypesManager {
      return project.service<FrontendXBreakpointTypesManagerService>().also {
        it.awaitInitialized()
      }
    }
  }
}


@Service(Service.Level.PROJECT)
private class FrontendXBreakpointTypesManagerService(
  private val project: Project,
  private val cs: CoroutineScope,
) : FrontendXBreakpointTypesManager {
  private val initialized = CompletableDeferred<Unit>()

  private val types = ConcurrentHashMap<XBreakpointTypeId, XBreakpointTypeProxy>()

  init {
    cs.launch {
      val (initialBreakpointTypes, breakpointTypesFlow) = XBreakpointTypeApi.getInstance().getBreakpointTypeList(project.projectId())
      handleBreakpointTypesFromBackend(initialBreakpointTypes)
      initialized.complete(Unit)

      breakpointTypesFlow.toFlow().collectLatest {
        handleBreakpointTypesFromBackend(it)
      }
    }
  }

  suspend fun awaitInitialized() {
    initialized.await()
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


  override fun getTypeById(id: XBreakpointTypeId): XBreakpointTypeProxy? {
    return types[id]
  }

  override fun getBreakpointTypes(): List<XBreakpointTypeProxy> {
    return types.values.toList()
  }

  override fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy> {
    return types.values.filterIsInstance<XLineBreakpointTypeProxy>()
  }
}