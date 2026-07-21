// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ContentPreloadable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.patchVersion
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope

internal suspend fun <T> retryUntilVersionMatch(project: Project, document: Document?, request: suspend (DocumentPatchVersion?) -> T?): T {
  while (true) {
    val version = document?.patchVersion(project)
    val result = request(version)
    if (result != null) return result
  }
}

internal suspend fun durableWithStateReset(block: suspend CoroutineScope.() -> Unit, stateReset: () -> Unit) = durable {
  try {
    block()
  }
  finally {
    stateReset()
  }
}

/**
 * Call this function to ensure the file content is loaded, so the further operations can be performed without a blocking RPC request.
 *
 * In remdev, [com.intellij.openapi.fileEditor.FileDocumentManager.getDocument] makes blocking request to load the file content.
 * However, it is called inside read action, which makes this a potential slow-op.
 */
internal suspend fun VirtualFile.ensureContentLoaded() {
  (this as? ContentPreloadable)?.preloadContent()
}
