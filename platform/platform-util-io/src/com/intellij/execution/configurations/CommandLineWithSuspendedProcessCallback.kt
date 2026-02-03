// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import java.util.function.LongConsumer

/**
 * Provides an ability to call a custom user's logic for a Windows process instance that has been started in a suspended state.
 * Implementation should have the next guarantees:
 * - the Windows process has to be started in the suspended state
 * - the user-provided callback is being invoked after process instance creation
 * - the callback should be invoked in the same thread right after the process was created
 * - the Windows process should be resumed only after the provided callback was being invoked
 */
interface CommandLineWithSuspendedProcessCallback {
  fun withWinSuspendedProcessCallback(callback: LongConsumer)
  fun getWinSuspendedProcessCallback(): LongConsumer?
}
