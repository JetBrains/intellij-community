// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * A provider of events from [XDebugSession] which could trigger UI updates.
 */
@ApiStatus.Internal
interface XDebugSessionEventsProvider {
  fun getUiUpdateEventsFlow(): Flow<Unit>
}