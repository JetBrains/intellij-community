// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import java.io.DataInput
import java.io.DataOutput

/**
 * Helper class for serializing and deserializing Int based [com.intellij.vcs.log.VcsRefType]
 * This ensures compatibility of API [com.intellij.vcs.log.VcsLogRefManager.serialize] and [com.intellij.vcs.log.VcsLogRefManager.deserialize]
 */
class VcsRefTypeSerializer : DataInput, DataOutput {

  private var type = 0

  override fun readInt(): Int = type

  override fun writeInt(v: Int) {
    type = v
  }

  override fun readFully(b: ByteArray) {}

  override fun readFully(b: ByteArray, off: Int, len: Int) {}

  override fun skipBytes(n: Int): Int {
    throw UnsupportedOperationException()
  }

  override fun readBoolean(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun readByte(): Byte {
    throw UnsupportedOperationException()
  }

  override fun readUnsignedByte(): Int {
    throw UnsupportedOperationException()
  }

  override fun readShort(): Short {
    throw UnsupportedOperationException()
  }

  override fun readUnsignedShort(): Int {
    throw UnsupportedOperationException()
  }

  override fun readChar(): Char {
    throw UnsupportedOperationException()
  }

  override fun readLong(): Long {
    throw UnsupportedOperationException()
  }

  override fun readFloat(): Float {
    throw UnsupportedOperationException()
  }

  override fun readDouble(): Double {
    throw UnsupportedOperationException()
  }

  override fun readLine(): String {
    throw UnsupportedOperationException()
  }

  override fun readUTF(): String {
    throw UnsupportedOperationException()
  }

  override fun write(b: Int) {}

  override fun write(b: ByteArray) {}

  override fun write(b: ByteArray, off: Int, len: Int) {}

  override fun writeBoolean(v: Boolean) {}

  override fun writeByte(v: Int) {}

  override fun writeShort(v: Int) {}

  override fun writeChar(v: Int) {}

  override fun writeLong(v: Long) {}

  override fun writeFloat(v: Float) {}

  override fun writeDouble(v: Double) {}

  override fun writeBytes(s: String) {}

  override fun writeChars(s: String) {}

  override fun writeUTF(s: String) {}
}
