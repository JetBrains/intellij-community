// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.UnInterruptibleFileChannel
import java.io.*
import java.nio.channels.Channels
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.reflect.KProperty

abstract class PersistentVar<T>(
  path: Path
) {
  init {
    FileUtil.createIfNotExists(path.toFile())
  }

  protected val fileChannel = UnInterruptibleFileChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)

  abstract fun DataInput.readValue(): T?
  abstract fun DataOutput.writeValue(value: T)

  @Throws(IOException::class)
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = synchronized(this) {
    DataInputStream(Channels.newInputStream(fileChannel.position(0))).readValue()
  }

  @Throws(IOException::class)
  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?): Unit = synchronized(this) {
    if (value == null) {
      fileChannel.truncate(0)
    }
    else {
      DataOutputStream(Channels.newOutputStream(fileChannel.position(0))).writeValue(value)
    }
    fileChannel.force(false)
  }

  companion object {
    fun integer(path: Path) = object : PersistentVar<Int>(path) {
      override fun DataInput.readValue(): Int? =
        try {
          readInt()
        }
        catch (e: EOFException) {
          null
        }

      override fun DataOutput.writeValue(value: Int) = writeInt(value)
    }

    fun long(path: Path) = object : PersistentVar<Long>(path) {
      override fun DataInput.readValue(): Long? =
        try {
          readLong()
        }
        catch (e: EOFException) {
          null
        }

      override fun DataOutput.writeValue(value: Long) = writeLong(value)
    }
  }
}