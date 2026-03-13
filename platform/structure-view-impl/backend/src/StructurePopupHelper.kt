// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend

import com.intellij.ide.rpc.rpcId
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.platform.structureView.impl.SHOW_STRUCTURE_POPUP_REMOTE_TOPIC
import com.intellij.platform.structureView.impl.ShowStructurePopupRequest
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

//todo: currently unused
@ApiStatus.Internal
fun showFileStructurePopup(
  project: Project,
  fileEditor: FileEditor,
  virtualFile: VirtualFile,
  callback: (AbstractTreeNode<*>) -> Unit
) {
  if (Registry.`is`("frontend.structure.popup")) {
    StructureViewScopeHolder.getInstance(project).cs.launch {
      val request = ShowStructurePopupRequest(
        fileEditor.rpcId(),
        virtualFile.rpcId(),
        virtualFile.name,
      )
      SHOW_STRUCTURE_POPUP_REMOTE_TOPIC.sendToClient(project, request)
    }
  }
}
