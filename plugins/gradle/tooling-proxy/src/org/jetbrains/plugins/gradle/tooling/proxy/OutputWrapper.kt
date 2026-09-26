// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import java.io.OutputStream
import java.nio.charset.StandardCharsets

class OutputWrapper(private val listener: (String) -> Unit) : OutputStream() {
  private var myBuffer: StringBuilder? = null
  override fun write(b: Int) {
    if (myBuffer == null) {
      myBuffer = StringBuilder()
    }
    myBuffer!!.append(b.toChar())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    if (myBuffer == null) {
      myBuffer = StringBuilder()
    }
    myBuffer!!.append(String(b, off, len, StandardCharsets.UTF_8))
  }

  override fun flush() {
    doFlush()
  }

  private fun doFlush() {
    if (myBuffer == null) return
    listener.invoke(myBuffer.toString())
    myBuffer!!.setLength(0)
  }
}