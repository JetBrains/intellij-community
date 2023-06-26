// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import org.jetbrains.annotations.ApiStatus

/**
 * Bi-directional iterator
 */
@ApiStatus.Experimental
interface BiDiIterator<T> : Iterator<T> {
  fun hasPrevious(): Boolean
  fun previous(): T
}