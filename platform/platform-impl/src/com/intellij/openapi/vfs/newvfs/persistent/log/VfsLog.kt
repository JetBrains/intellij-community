// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.div

class VfsLog(
  private val storagePath: Path
) {
  @OptIn(DelicateCoroutinesApi::class)
  private val coroutineDispatcher = newSingleThreadContext("VFS WAL dispatcher")
  private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

  init {
    FileUtil.ensureExists(storagePath.toFile())
  }

  val interceptors = listOf<ConnectionInterceptor>(
    ContentsLogInterceptor(storagePath / "contents"),
    AttributesLogInterceptor(storagePath / "attributes"),
    RecordsLogInterceptor(storagePath / "records")
  )

  fun dispose() {
    coroutineScope.cancel("dispose")
  }
}