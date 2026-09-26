// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * The entity is intended as an internal storage for module-level services persistent components.
 */
@Internal
interface CustomImlComponentEntity : WorkspaceEntity {
  @Parent
  val module: ModuleEntity

  /** Key component name, value Raw XML bean content. */
  val components: Map<String, String>
}

@get:Internal
val ModuleEntity.customImlComponent: CustomImlComponentEntity?
  by WorkspaceEntity.extension()
