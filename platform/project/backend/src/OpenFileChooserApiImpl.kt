// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.OpenFileChooserApi
import com.intellij.platform.project.OpenFileChooserService
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

internal class OpenFileChooserApiImpl : OpenFileChooserApi {
  override suspend fun chooseDirectory(projectId: ProjectId, initialDirectory: String): Deferred<String?> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred(null)
    val descriptor: FileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    descriptor.isForcedToUseIdeaFileChooser = true
    val deferred = CompletableDeferred<String?>()

    OpenFileChooserService.getInstance().coroutineScope.launch(Dispatchers.EDT) {
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        FileChooser.chooseFiles(descriptor, project, null, VfsUtil.findFileByIoFile(File(initialDirectory), true), object : FileChooser.FileChooserConsumer {
          override fun consume(files: MutableList<VirtualFile?>) {
            deferred.complete(files[0]?.presentableUrl)
          }

          override fun cancelled() {
            deferred.complete(null)
          }
        })
      }
    }
    return deferred
  }
}

private class OpenFileChooserApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OpenFileChooserApi>()) {
      OpenFileChooserApiImpl()
    }
  }
}