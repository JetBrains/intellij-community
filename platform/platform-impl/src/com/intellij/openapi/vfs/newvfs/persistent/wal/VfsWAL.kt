// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.wal

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.util.ConnectionInterceptor
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.div

class VfsWAL(
  private val storagePath: Path
) {
  @OptIn(DelicateCoroutinesApi::class)
  private val coroutineDispatcher = newSingleThreadContext("VFS WAL dispatcher")
  private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

  init {
    FileUtil.ensureExists(storagePath.toFile())
  }

  val interceptors = listOf<ConnectionInterceptor>(
    ContentsWALInterceptor(storagePath / "contents"),
    AttributesWALInterceptor(storagePath / "attributes"),
    RecordsWALInterceptor(storagePath / "records")
  )

  fun dispose() {
    coroutineScope.cancel("dispose")
  }
}