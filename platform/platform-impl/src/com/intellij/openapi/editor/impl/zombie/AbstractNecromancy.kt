// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import java.io.DataInput
import java.io.DataOutput


abstract class AbstractNecromancy<Z : Zombie>(
  private val spellLevel: Int,
  private val isDeepBury: Boolean,
) : Necromancy<Z> {

  final override fun spellLevel(): Int = spellLevel

  final override fun isDeepBury(): Boolean = isDeepBury

  protected fun writeInt(grave: DataOutput, value: Int) {
    DataInputOutputUtil.writeINT(grave, value)
  }

  protected fun readInt(grave: DataInput): Int {
    return DataInputOutputUtil.readINT(grave)
  }

  protected fun writeLong(grave: DataOutput, value: Long) {
    DataInputOutputUtil.writeLONG(grave, value)
  }

  protected fun readLong(grave: DataInput): Long {
    return DataInputOutputUtil.readLONG(grave)
  }

  protected fun writeString(grave: DataOutput, value: String) {
    IOUtil.writeUTF(grave, value)
  }

  protected fun readString(grave: DataInput): String {
    return IOUtil.readUTF(grave)
  }

  protected fun writeBool(grave: DataOutput, value: Boolean) {
    grave.writeBoolean(value)
  }

  protected fun readBool(grave: DataInput): Boolean {
    return grave.readBoolean()
  }

  protected fun writeIntNullable(grave: DataOutput, value: Int?) {
    writeNullable(grave, value) {
      writeInt(grave, it)
    }
  }

  protected fun readIntNullable(grave: DataInput): Int? {
    return readNullable(grave) {
      readInt(grave)
    }
  }

  protected fun writeLongNullable(grave: DataOutput, value: Long?) {
    writeNullable(grave, value) {
      writeLong(grave, it)
    }
  }

  protected fun readLongNullable(grave: DataInput): Long? {
    return readNullable(grave) {
      readLong(grave)
    }
  }

  protected fun writeStringNullable(grave: DataOutput, value: String?) {
    writeNullable(grave, value) {
      writeString(grave, it)
    }
  }

  protected fun readStringNullable(grave: DataInput): String? {
    return readNullable(grave) {
      readString(grave)
    }
  }

  protected fun <T> writeNullable(grave: DataOutput, value: T?, write: (T) -> Unit) {
    if (value == null) {
      writeBool(grave, false)
    } else {
      writeBool(grave, true)
      write(value)
    }
  }

  protected fun <T> readNullable(grave: DataInput, read: () -> T): T? {
    return if (readBool(grave)) {
      read()
    } else {
      null
    }
  }
}
