// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import org.jetbrains.annotations.ApiStatus

/**
 * Marks [WorkspaceFileIndexContributor] defined in platform, and having [IndexableEntityProvider] duplicating their data.
 * Interface is designed to support backward compatibility of platform only, and shouldn't be used in plugin code.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface PlatformInternalWorkspaceFileIndexContributor