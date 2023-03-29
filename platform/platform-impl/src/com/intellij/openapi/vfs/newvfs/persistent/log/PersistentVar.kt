// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.FileChannelInterruptsRetryer
import java.io.*
import java.nio.channels.Channels
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.reflect.KProperty

abstract class PersistentVar<T>(
  path: Path
) {
  init {
    FileUtil.createIfNotExists(path.toFile())
  }

  protected val fileChannelRetryer = FileChannelInterruptsRetryer(path, EnumSet.of(READ, WRITE, CREATE))

  abstract fun DataInput.readValue(): T?
  abstract fun DataOutput.writeValue(value: T)

  @Throws(IOException::class)
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = synchronized(this) {
    return fileChannelRetryer.retryIfInterrupted {
      DataInputStream(Channels.newInputStream(it.position(0))).readValue()
    }
  }

  @Throws(IOException::class)
  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?): Unit = synchronized(this) {
    fileChannelRetryer.retryIfInterrupted {
      if (value == null) {
        it.truncate(0)
      }
      else {
        DataOutputStream(Channels.newOutputStream(it.position(0))).writeValue(value)
      }
      it.force(false)
    }
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