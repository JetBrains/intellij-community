// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.diff

import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.vfs.VirtualFile

internal fun VirtualFile.isDiffVirtualFile(): Boolean =
  this is DiffVirtualFileBase
  // Instance type is lost on frontend in split mode
  || getUserData(DiffFrontendDataKeys.IS_DIFF_SPLIT_VIRTUAL_FILE) != null