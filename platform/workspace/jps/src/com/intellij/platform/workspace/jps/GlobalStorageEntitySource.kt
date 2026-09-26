// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.annotations.ApiStatus

/**
 * A specialized version of [com.intellij.platform.workspace.storage.EntitySource] used for entities that need to be synchronized
 * by the platform between GlobalWorkspaceModel and WorkspaceModel.
 *
 * Currently only [LibraryEntity] and [SdkEntity] are synchronized between global WSM and project WSM.
 * There are no plans to allow synchronization between WSMs for custom entities.
 * See documentation of GlobalWorkspaceModel for more information.
 */
@ApiStatus.Internal
public interface GlobalStorageEntitySource : EntitySource