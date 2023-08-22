// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.CoroutineScope

const val TEST_TIMEOUT_MS: Long = 1000

@Suppress("DeprecatedCallableAddReplaceWith") // function move does not work correctly
@Deprecated(
  message = "Function was moved to `com.intellij.testFramework.common`",
)
fun timeoutRunBlocking(action: suspend CoroutineScope.() -> Unit) {
  com.intellij.testFramework.common.timeoutRunBlocking(action = action)
}
