// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val ChangeListManager.unversionedFiles: List<VirtualFile>
  get() = getUnversionedFilesPaths().mapNotNull { it.virtualFile }