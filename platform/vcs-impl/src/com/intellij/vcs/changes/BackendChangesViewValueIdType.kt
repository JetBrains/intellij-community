// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.vcs.changes.ChangesViewId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.vcs.changes.viewModel.BackendCommitChangesViewModel

internal object BackendChangesViewValueIdType : BackendValueIdType<ChangesViewId, BackendCommitChangesViewModel>(::ChangesViewId)