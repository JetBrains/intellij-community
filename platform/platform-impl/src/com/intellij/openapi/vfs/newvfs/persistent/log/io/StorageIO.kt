// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import java.io.Closeable
import java.io.Flushable

interface StorageIO : RandomAccessBufferIO, Flushable, Closeable {
  fun offsetOutputStream(startPosition: Long): OutputStreamWithValidation
}