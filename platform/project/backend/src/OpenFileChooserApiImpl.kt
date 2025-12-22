// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend

import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.project.OpenFileChooserApi
import com.intellij.platform.project.OpenFileChooserService
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.util.cancelOnDispose
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
    deferred.cancelOnDispose(project)

    OpenFileChooserService.getInstance(project).coroutineScope.launch(Dispatchers.EDT) {
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        val files = FileChooser.chooseFiles(descriptor, project, VfsUtil.findFileByIoFile(File(initialDirectory), true))
        deferred.complete(files.firstOrNull()?.presentableUrl)
      }
    }
    return deferred
  }
}

internal class OpenFileChooserApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OpenFileChooserApi>()) {
      OpenFileChooserApiImpl()
    }
  }
}