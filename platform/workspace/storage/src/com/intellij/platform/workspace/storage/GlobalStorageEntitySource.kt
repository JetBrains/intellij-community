// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

/**
 * A specialized version of [EntitySource] used for entities that need to be synchronized
 * by the platform between GlobalWorkspaceModel and WorkspaceModel.
 *
 * For example [com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource] uses this type of [EntitySource].
 */
public interface GlobalStorageEntitySource : EntitySource