// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectEntity
import com.intellij.openapi.project.impl.asProject
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.RemoteValueHint
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.jetbrains.rhizomedb.entity
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import java.awt.Point
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private class BackendDebuggerValueLookupHintsRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>()) {
      BackendDebuggerValueLookupHintsRemoteApi()
    }
  }
}

private class BackendDebuggerValueLookupHintsRemoteApi : XDebuggerValueLookupHintsRemoteApi {
  override suspend fun canShowHint(projectId: UID, editorId: EditorId, offset: Int, hintType: ValueHintType): Boolean {
    return withContext(Dispatchers.Main) {
      val project = getProjectFromUID(projectId) ?: return@withContext false
      val editor = editorId.findEditor()
      val point = editor.offsetToXY(offset)

      val hint = getValueHintFromDebuggers(project, editor, point, hintType) ?: return@withContext false
      hint.canShowHint()
    }
  }

  override suspend fun createHint(projectId: UID, editorId: EditorId, offset: Int, hintType: ValueHintType): RemoteValueHint? {
    return withContext(Dispatchers.Main) {
      val project = getProjectFromUID(projectId) ?: return@withContext null
      val editor = editorId.findEditor()
      val point = editor.offsetToXY(offset)

      val hint = getValueHintFromDebuggers(project, editor, point, hintType) ?: return@withContext null
      val hintId = BackendDebuggerValueLookupHintsHolder.getInstance(project).registerNewHint(hint)
      RemoteValueHint(hintId)
    }
  }

  private suspend fun getValueHintFromDebuggers(
    project: Project,
    editor: Editor,
    point: Point,
    hintType: ValueHintType,
  ): AbstractValueHint? {
    for (support in DebuggerSupport.getDebuggerSupports()) {
      val handler = support.quickEvaluateHandler
      if (handler.isEnabled(project)) {
        val hint = handler.createValueHintAsync(project, editor, point, hintType).hintPromise.await()
        if (hint != null) {
          return hint
        }
      }
    }
    return null
  }

  override suspend fun showHint(projectId: UID, hintId: Int): Flow<Unit> {
    val project = getProjectFromUID(projectId) ?: return emptyFlow()
    val hint = BackendDebuggerValueLookupHintsHolder.getInstance(project).getHintById(hintId) ?: return emptyFlow()
    return callbackFlow {
      withContext(Dispatchers.Main) {
        hint.invokeHint {
          trySend(Unit)
          close()
        }
      }
      awaitClose()
    }
  }

  override suspend fun removeHint(projectId: UID, hintId: Int) {
    val project = getProjectFromUID(projectId) ?: return
    val hint = BackendDebuggerValueLookupHintsHolder.getInstance(project).getHintById(hintId) ?: return
    BackendDebuggerValueLookupHintsHolder.getInstance(project).removeHint(hintId)
    withContext(Dispatchers.Main) {
      hint.hideHint()
    }
  }

  private suspend fun getProjectFromUID(projectId: UID): Project? {
    return withKernel {
      val projectEntity = entity<ProjectEntity, UID>(ProjectEntity.ProjectId, projectId)
      projectEntity?.asProject()
    }
  }
}

// exposed only for backend.split part
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BackendDebuggerValueLookupHintsHolder(project: Project) {
  private val idCounter = AtomicInteger()
  private val hints = ConcurrentHashMap<Int, AbstractValueHint>()

  fun registerNewHint(hint: AbstractValueHint): Int {
    val newHintId = idCounter.incrementAndGet()
    hints[newHintId] = hint
    return newHintId
  }

  fun removeHint(hintId: Int) {
    hints.remove(hintId)
  }

  fun getHintById(hintId: Int): AbstractValueHint? {
    return hints[hintId]
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendDebuggerValueLookupHintsHolder = project.service<BackendDebuggerValueLookupHintsHolder>()
  }
}