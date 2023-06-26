// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import org.jetbrains.annotations.ApiStatus

/**
 * Used in case computation holds a resource that must be closed even if computation is not invoked
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface CloseableComputable<R>: AutoCloseable {
  fun compute(): R
  operator fun invoke(): R = compute()
}