// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.wal

import com.intellij.openapi.vfs.newvfs.persistent.intercept.RecordsInterceptor
import java.nio.file.Path

class RecordsWALInterceptor(
  val storagePath: Path
): RecordsInterceptor {
}