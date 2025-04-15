// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class RepositoryId(val projectId: ProjectId, val rootPath: VirtualFileId)