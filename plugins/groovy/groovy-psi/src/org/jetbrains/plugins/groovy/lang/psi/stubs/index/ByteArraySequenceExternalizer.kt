/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.stubs.index

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import java.io.DataInput
import java.io.DataOutput

object ByteArraySequenceExternalizer : DataExternalizer<ByteArraySequence> {

  override fun save(out: DataOutput, value: ByteArraySequence) {
    writeINT(out, value.length)
    out.write(value.bytes, value.offset, value.length)
  }

  override fun read(input: DataInput): ByteArraySequence {
    val length = readINT(input)
    val buffer = ByteArray(length)
    input.readFully(buffer)
    return ByteArraySequence(buffer)
  }
}
