// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.util.FileStructureUtil
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.structureView.impl.ShowStructurePopupRequest
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.util.application
import fleet.rpc.client.durable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private val nextBackendModelId = AtomicInteger(-1)

@ApiStatus.Internal
@JvmOverloads
fun showFileStructurePopup(
  project: Project,
  fileEditor: FileEditor,
  virtualFile: VirtualFile?,
  @NlsContexts.PopupTitle title: String? = null,
  callback: ((AbstractTreeNode<*>) -> Unit)? = null,
) {
  if (!FileStructureUtil.isSplitPopupEnabled()) {
    val popup = ViewStructureAction.createPopup(project, fileEditor, callback)
    if (popup == null) return

    popup.setTitle(title ?: virtualFile?.name ?: fileEditor.name)
    popup.show()
    return
  }

  StructureViewScopeHolder.getInstance(project).cs.launch {
    val structureTreeService = application.service<BackendStructureTreeService>()
    val modelId = StructureViewDtoId(nextBackendModelId.getAndDecrement())
    val modelDto = structureTreeService.createStructureViewModel(modelId, project, fileEditor, callback)
    if (modelDto == null) return@launch

    val receipt = Channel<Unit>(capacity = 1)
    try {
      try {
        val request = ShowStructurePopupRequest(
          project.projectId(),
          modelId,
          modelDto,
          virtualFile?.rpcId(),
          title ?: virtualFile?.name ?: fileEditor.name,
          receipt
        )
        durable {
          structureTreeService.emitShowPopupRequest(request)
        }
      }
      catch (t: Throwable) {
        structureTreeService.disposeStructureViewModel(modelId)
        throw t
      }

      if (withTimeoutOrNull(5_000.milliseconds) { receipt.receiveCatching().getOrNull() } == null) {
        fileLogger().warn("Frontend did not receive structure popup request for modelId=$modelId")
        structureTreeService.disposeStructureViewModel(modelId)
      }
    }
    finally {
      receipt.cancel()
    }
  }
}
