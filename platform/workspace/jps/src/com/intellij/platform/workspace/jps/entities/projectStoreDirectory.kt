// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

/** Tracks the project store directory of a directory-based project. */
@Internal
interface ProjectStoreDirectoryEntity : WorkspaceEntity {
  val url: VirtualFileUrl
}
