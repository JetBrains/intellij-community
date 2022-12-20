// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry

class VfsLog(
  private val storagePath: Path
) {
  private var version by PersistentVar.integer(storagePath / "version")
  init {
    version.let {
      if (it != VERSION) {
        if (it != null) {
          clear()
        }
        version = VERSION
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val coroutineDispatcher = newSingleThreadContext("VFS WAL dispatcher")
  private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

  val interceptors = listOf<ConnectionInterceptor>(
    ContentsLogInterceptor(storagePath / "contents"),
    AttributesLogInterceptor(storagePath / "attributes"),
    RecordsLogInterceptor(storagePath / "records")
  )

  fun dispose() {
    coroutineScope.cancel("dispose")
  }

  fun clear() {
    storagePath.forEachDirectoryEntry { child ->
      if (child != storagePath / "version") {
        child.delete(true)
      }
    }
  }

  companion object {
    const val VERSION = 1
  }
}