// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import java.io.OutputStream

abstract class OutputStreamWithValidation : OutputStream() {
  abstract fun validateWrittenBytesCount(expectedBytesWritten: Long)
}