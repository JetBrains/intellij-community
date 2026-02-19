// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.annotations.ApiStatus

/**
 * A specialized version of [com.intellij.platform.workspace.storage.EntitySource] used for entities that need to be synchronized
 * by the platform between GlobalWorkspaceModel and WorkspaceModel.
 *
 * This may be needed if you create a [com.intellij.platform.workspace.storage.WorkspaceEntity] in GlobalWorkspaceModel and want it to be copied to WorkspaceModel of a project.
 *
 * For example [com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource] uses this type of [com.intellij.platform.workspace.storage.EntitySource].
 */
@ApiStatus.Internal
public interface GlobalStorageEntitySource : EntitySource