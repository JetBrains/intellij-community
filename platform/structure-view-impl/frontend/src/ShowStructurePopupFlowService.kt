// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.frontend

import com.intellij.ide.util.FileStructureUtil
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.structureView.frontend.uiModel.StructureUiModelImpl
import com.intellij.platform.structureView.impl.StructureTreeApi
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
internal class ShowStructurePopupFlowService(cs: CoroutineScope) {
  init {
    cs.launch {
      if (!FileStructureUtil.isSplitPopupEnabled()) return@launch
      durable {
        StructureTreeApi.getInstance().getShowPopupRequestFlow().collect { request ->
          try {
            request.received.send(Unit)
          }
          catch (_: Throwable) {
            StructureTreeApi.callDisposeModel(request.modelId)
            return@collect
          }
          withContext(Dispatchers.EDT) {
            val project = request.projectId.findProjectOrNull()
            val file = request.fileId?.virtualFile()

            if (project == null || project.isDisposed) {
              StructureTreeApi.callDisposeModel(request.modelId)
              return@withContext
            }

            try {
              val model = StructureUiModelImpl(file, project, request.modelId, request.model)
              val popup = FileStructurePopup(project, null, model)
              request.title?.let { popup.setTitle(it) }
              popup.show()
            }
            catch (t: Throwable) {
              StructureTreeApi.callDisposeModel(request.modelId)
              throw t
            }
          }
        }
      }
    }
  }

  companion object {
    fun getInstance(): ShowStructurePopupFlowService = service()
  }
}

@ApiStatus.Internal
internal class ShowStructurePopupFlowStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ShowStructurePopupFlowService.getInstance()
  }
}
