// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import kotlinx.coroutines.Deferred

/**
 * RPC requests have internal timeout, so returning a result of a long computation may be canceled.
 * To prevent this, we wrap the result in a Deferred.
 */
typealias TimeoutSafeResult<T> = Deferred<T>
