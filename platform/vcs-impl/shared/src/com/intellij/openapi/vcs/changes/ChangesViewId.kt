// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.ui.split.SplitComponentBinding
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val ChangesViewSplitComponentBinding: SplitComponentBinding<ChangesViewId> = SplitComponentBinding("ChangesView", ::ChangesViewId)

/**
 * Note that there is only 1 changes view per project.
 */
@Serializable
@ApiStatus.Internal
data class ChangesViewId(override val uid: UID) : Id
