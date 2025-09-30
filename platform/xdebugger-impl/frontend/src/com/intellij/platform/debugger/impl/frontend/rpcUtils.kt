// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
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

internal suspend fun durableWithStateReset(block: suspend CoroutineScope.() -> Unit, stateReset: () -> Unit) = try {
  durable(body = block)
}
finally {
  stateReset()
}
