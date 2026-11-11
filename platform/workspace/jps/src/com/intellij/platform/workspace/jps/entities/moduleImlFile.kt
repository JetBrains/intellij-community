// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.IndexVfu
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

/** Tracks the module file, including a file that does not exist yet. */
@Internal
interface ModuleImlFileEntity : WorkspaceEntity {
  @IndexVfu
  val url: VirtualFileUrl

  @Parent
  val module: ModuleEntity
}

@get:Internal
val ModuleEntity.moduleImlFile: ModuleImlFileEntity? by WorkspaceEntity.extension()
