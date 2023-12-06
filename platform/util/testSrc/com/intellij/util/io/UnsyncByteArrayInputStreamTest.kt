// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream

class UnsyncByteArrayInputStreamTest {
  @Test
  fun `second constructor accounts offset and length correctly`() {
    val data = ByteArrayOutputStream().run {
      write(1)
      DataOutputStream(this).run {
        writeInt(2)
        writeInt(3)
        writeInt(4)
      }
      toByteArray()
    }
    assert(data.size == 13)

    fun readInts(inp: InputStream) {
      DataInputStream(inp).run {
        assert(readInt() == 2)
        assert(readInt() == 3)
        assert(read() == -1) // EOF
      }
    }

    val unsync = UnsyncByteArrayInputStream(data, 1, 8)
    val base = ByteArrayInputStream(data, 1, 8)

    readInts(base)
    readInts(unsync)
  }

  @Test
  fun `marked position is equal to offset initially`() {
    val data = byteArrayOf(1, 2, 3, 4, 5)
    fun InputStream.test(): Int {
      readNBytes(3)
      reset()
      return read()
    }
    for (i in 0..2) {
      assert(ByteArrayInputStream(data, i, 5).test() == data[i].toInt())
      assert(UnsyncByteArrayInputStream(data, i, 5).test() == data[i].toInt())
    }
  }
}