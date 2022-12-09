// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.util

import com.intellij.openapi.util.io.ByteArraySequence

sealed interface ConnectionInterceptor

interface ContentsInterceptor : ConnectionInterceptor {
  fun onWriteBytes(record: Int, bytes: ByteArraySequence, fixedSize: Boolean)
}

interface RecordsInterceptor : ConnectionInterceptor
interface AttributesInterceptor : ConnectionInterceptor