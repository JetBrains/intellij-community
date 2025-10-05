// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.patchVersion

internal fun Document.documentVersionMatches(project: Project, version: DocumentPatchVersion?): Boolean {
  if (version == null) return true
  val localVersion = patchVersion(project) ?: return true
  return version == localVersion
}
