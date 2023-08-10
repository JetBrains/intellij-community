// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.FileChannelInterruptsRetryer
import java.io.*
import java.nio.channels.Channels
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.reflect.KProperty

/**
 * Caches a value for fast reading. There should not be two or more instances on the same path.
 */
abstract class PersistentVar<T>(
  private val path: Path,
  private val readValue: DataInput.() -> T?,
  private val writeValue: DataOutput.(value: T) -> Unit
): AutoCloseable {
  private fun initFile(): FileChannelInterruptsRetryer {
    path.parent?.let { Files.createDirectories(it) }
    try {
      Files.createFile(path)
    }
    catch (ignore: FileAlreadyExistsException) {
    }
    return FileChannelInterruptsRetryer(path, EnumSet.of(READ, WRITE, CREATE))
  }

  @Volatile
  private var fileChannelRetryer = initFile()

  private fun readValue(): T? = fileChannelRetryer.retryIfInterrupted {
    DataInputStream(Channels.newInputStream(it.position(0))).readValue()
  }

  @Volatile
  protected var cachedValue: T? = readValue()

  fun getValue() = cachedValue

  fun setValue(value: T?): Unit = synchronized(this) {
    fileChannelRetryer.retryIfInterrupted {
      if (value == null) {
        it.truncate(0)
      }
      else {
        DataOutputStream(Channels.newOutputStream(it.position(0))).writeValue(value)
      }
      it.force(false)
      cachedValue = value
    }
  }

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = getValue()

  @Throws(IOException::class)
  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?): Unit = setValue(value)

  override fun close() = synchronized(this) {
    cachedValue = null
    fileChannelRetryer.close()
  }

  fun reopen() = synchronized(this) {
    fileChannelRetryer.close()
    fileChannelRetryer = initFile()
    cachedValue = readValue()
  }

  companion object {
    fun integer(path: Path): PersistentVar<Int> = object : PersistentVar<Int>(
      path,
      readValue = {
        try {
          readInt()
        }
        catch (e: EOFException) {
          null
        }
      },
      writeValue = { writeInt(it) }
    ) {}

    fun long(path: Path): PersistentVar<Long> = object : PersistentVar<Long>(
      path,
      readValue = {
        try {
          readLong()
        }
        catch (e: EOFException) {
          null
        }
      },
      writeValue = { writeLong(it) }
    ) {}
  }
}