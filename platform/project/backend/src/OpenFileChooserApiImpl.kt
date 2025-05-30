// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.OpenFileChooserApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class OpenFileChooserApiImpl : OpenFileChooserApi {
  override suspend fun chooseDirectory(projectId: ProjectId, initialDirectory: String): String? {
    val project = projectId.findProjectOrNull() ?: return null
    val descriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    descriptor.isForcedToUseIdeaFileChooser = true
    val deferred = CompletableDeferred<String?>()

    withContext(Dispatchers.EDT) {
      FileChooser.chooseFiles(descriptor, project, null, VfsUtil.findFileByIoFile(File(initialDirectory), true), object : FileChooser.FileChooserConsumer {
        override fun consume(files: MutableList<VirtualFile?>) {
          deferred.complete(files[0]?.presentableUrl)
        }

        override fun cancelled() {
          deferred.complete(null)
        }
      })
    }
    return deferred.await()
  }
}

private class OpenFileChooserApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OpenFileChooserApi>()) {
      OpenFileChooserApiImpl()
    }
  }
}