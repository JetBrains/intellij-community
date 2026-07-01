// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.patchVersion
import com.intellij.platform.debugger.impl.shared.awaitCommited
import com.intellij.platform.rpc.backend.impl.DocumentSync

internal suspend fun Document.awaitIsInSyncAndCommitted(project: Project, version: DocumentPatchVersion?): Boolean {
  DocumentSync.awaitDocumentSync()
  if (!versionMatches(project, version)) return false
  awaitCommited(project)
  return true
}

private fun Document.versionMatches(project: Project, version: DocumentPatchVersion?): Boolean {
  if (version == null) return true
  val localVersion = patchVersion(project) ?: return true
  return version == localVersion
}

