// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import org.jetbrains.annotations.ApiStatus

/**
 * This [WorkspaceFileSetData] interface may be used to avoid adding
 * associated [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet] root as watched to VFS.
 * <p>
 * As a rule of thumb roots registered in project should be watched by VFS, otherwise it won't acknowledge IDE about changes under it.
 * But sometimes, when registered roots are under already registered ones (for example, content roots),
 * and there is significant number of them (up to 500 000), it's better to simply ignore them.
 *
 */
@ApiStatus.Experimental
interface SkipAddingToWatchedRootsData : WorkspaceFileSetData
