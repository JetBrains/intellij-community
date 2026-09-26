// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ProjectViewUpdateRequestsService(
  private val scope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): ProjectViewUpdateRequestsService = project.service()
  }

  internal val updates: SharedFlow<FileUpdate>
    field = MutableSharedFlow(extraBufferCapacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun requestUpdate(file: VirtualFile, deep: Boolean) {
    updates.tryEmit(FileUpdate(file, deep))
  }

  fun createRpcChannel(): SendChannel<FileUpdateDTO> {
    val result = Channel<FileUpdateDTO>(Channel.BUFFERED)
    scope.launch(CoroutineName("FileUpdateDTO RPC channel")) {
      for (fileUpdateDTO in result) {
        updates.tryEmit(fileUpdateDTO.fromDTO() ?: continue)
      }
    }
    return result
  }
}

@ApiStatus.Internal
data class FileUpdate(val file: VirtualFile, val deep: Boolean) {
  fun toDTO(): FileUpdateDTO {
    return FileUpdateDTO(file.rpcId(), deep)
  }
}

@ApiStatus.Internal
@Serializable
data class FileUpdateDTO(val fileId: VirtualFileId, val deep: Boolean) {
  fun fromDTO(): FileUpdate? {
    val file = fileId.virtualFile() ?: return null
    return FileUpdate(file, deep)
  }
}
