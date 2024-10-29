// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev

import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArray.Companion.OpenMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.test.assertContentEquals

class DurablePersistentByteArrayTest {
  @Test
  fun `basic sanity test`(@TempDir tempDir: Path) {
    val path = tempDir / "array.dat"
    if (path.exists()) path.deleteExisting()
    val initArr = ByteArray(16).also { it[0] = 1; it[8] = 1 }
    val durableArray = DurablePersistentByteArray.open(
      path, OpenMode.ReadWrite, 16
    ) { initArr }
    assertContentEquals(initArr, durableArray.getLastSnapshot())
    val upd = durableArray.commitChange {
      assertContentEquals(initArr, it)
      it[2] = 12
    }
    initArr[2] = 12
    assertContentEquals(initArr, upd)
    assertContentEquals(upd, durableArray.getLastSnapshot())
    durableArray.close()
    val durableArray2 = DurablePersistentByteArray.open(
      path, OpenMode.ReadWrite, 16
    ) { throw AssertionError() }
    assertContentEquals(upd, durableArray2.getLastSnapshot())
  }
}