// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jsonProtocol

import com.google.gson.stream.JsonWriter
import com.intellij.util.containers.isNullOrEmpty
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtf8Writer
import io.netty.buffer.ByteBufUtil
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.io.JsonUtil

open class OutMessage {
  val buffer: ByteBuf = ByteBufAllocator.DEFAULT.buffer()
  val writer: JsonWriter = JsonWriter(ByteBufUtf8Writer(buffer))

  private var finalized: Boolean = false

  init {
    writer.beginObject()
  }

  open fun beginArguments() {
  }

  fun writeMap(name: String, value: Map<String, String>? = null) {
    if (value == null) return

    beginArguments()
    writer.name(name)
    writer.beginObject()
    for ((key, value1) in value) {
      writer.name(key).value(value1)
    }
    writer.endObject()
  }

  protected fun writeLongArray(name: String, value: LongArray) {
    beginArguments()
    writer.name(name)
    writer.beginArray()
    for (v in value) {
      writer.value(v)
    }
    writer.endArray()
  }

  fun writeDoubleArray(name: String, value: DoubleArray) {
    beginArguments()
    writer.name(name)
    writer.beginArray()
    for (v in value) {
      writer.value(v)
    }
    writer.endArray()
  }

  fun writeIntArray(name: String, value: IntArray? = null) {
    if (value == null) {
      return
    }

    beginArguments()
    writer.name(name)
    writer.beginArray()
    for (v in value) {
      writer.value(v.toLong())
    }
    writer.endArray()
  }

  fun writeIntSet(name: String, value: IntSet) {
    beginArguments()
    writer.name(name)
    writer.beginArray()
    val iterator = value.iterator()
    while (iterator.hasNext()) {
      writer.value(iterator.nextInt().toLong())
    }
    writer.endArray()
  }

  fun writeIntList(name: String, value: IntList) {
      beginArguments()
      writer.name(name)
      writer.beginArray()
      for (i in 0 until value.size) {
        writer.value(value.getInt(i).toLong())
      }
      writer.endArray()
  }

  fun writeSingletonIntArray(name: String, value: Int) {
      beginArguments()
      writer.name(name)
      writer.beginArray()
      writer.value(value.toLong())
      writer.endArray()
  }

  fun <E : OutMessage> writeList(name: String, value: List<E>?) {
    if (value.isNullOrEmpty()) {
      return
    }

    beginArguments()
    writer.name(name)
    writer.beginArray()
    var isNotFirst = false
    for (item in value!!) {
      try {
        if (isNotFirst) {
          buffer.writeByte(','.toInt()).writeByte(' '.toInt())
        }
        else {
          isNotFirst = true
        }

        if (!item.finalized) {
          item.finalized = true
          try {
            item.writer.endObject()
          }
          catch (e: IllegalStateException) {
            if ("Nesting problem." == e.message) {
              throw RuntimeException(item.buffer.toString(Charsets.UTF_8) + "\nparent:\n" + buffer.toString(Charsets.UTF_8), e)
            }
            else {
              throw e
            }
          }
        }

        buffer.writeBytes(item.buffer)
      } finally {
        if (item.buffer.refCnt() > 0) {
          item.buffer.release()
        }
      }
    }
    writer.endArray()
  }

  fun writeStringList(name: String, value: Collection<String>?) {
    if (value == null) return
    beginArguments()
    JsonWriters.writeStringList(writer, name, value)
  }

  fun writeEnumList(name: String, values: Collection<Enum<*>>) {
    beginArguments()
    writer.name(name).beginArray()
    for (item in values) {
      writer.value(item.toString())
    }
    writer.endArray()
  }

  fun writeMessage(name: String, value: OutMessage?) {
    if (value == null) {
      return
    }
    try {
      beginArguments()
      prepareWriteRaw(this, name)

      if (!value.finalized) {
        value.close()
      }
      buffer.writeBytes(value.buffer)
    }
    finally {
      if (value.buffer.refCnt() > 0) {
        value.buffer.release()
      }
    }
  }

  fun close() {
    assert(!finalized)
    finalized = true
    writer.endObject()
    writer.close()
  }

  protected fun writeLong(name: String, value: Long) {
    beginArguments()
    writer.name(name).value(value)
  }

  fun writeString(name: String, value: String?) {
    if (value != null) {
      writeNullableString(name, value)
    }
  }

  fun writeNullableString(name: String, value: CharSequence?) {
    beginArguments()
    writer.name(name).value(value?.toString())
  }
}

fun prepareWriteRaw(message: OutMessage, name: String) {
  message.writer.name(name).nullValue()
  val itemBuffer = message.buffer
  itemBuffer.writerIndex(itemBuffer.writerIndex() - "null".length)
}

fun doWriteRaw(message: OutMessage, rawValue: String) {
  ByteBufUtil.writeUtf8(message.buffer, rawValue)
}

fun OutMessage.writeEnum(name: String, value: Enum<*>?, defaultValue: Enum<*>?) {
  if (value != null && value != defaultValue) {
    writeEnum(name, value)
  }
}

fun OutMessage.writeEnum(name: String, value: Enum<*>) {
  beginArguments()
  writer.name(name).value(value.toString())
}

fun OutMessage.writeString(name: String, value: CharSequence?, defaultValue: CharSequence?) {
  if (value != null && value != defaultValue) {
    writeString(name, value)
  }
}

fun OutMessage.writeString(name: String, value: CharSequence) {
  beginArguments()
  prepareWriteRaw(this, name)
  JsonUtil.escape(value, buffer)
}

fun OutMessage.writeInt(name: String, value: Int, defaultValue: Int) {
  if (value != defaultValue) {
    writeInt(name, value)
  }
}

fun OutMessage.writeInt(name: String, value: Int?) {
  if (value != null) {
    beginArguments()
    writer.name(name).value(value.toLong())
  }
}

fun OutMessage.writeBoolean(name: String, value: Boolean, defaultValue: Boolean) {
  if (value != defaultValue) {
    writeBoolean(name, value)
  }
}

fun OutMessage.writeBoolean(name: String, value: Boolean?) {
  if (value != null) {
    beginArguments()
    writer.name(name).value(value)
  }
}

fun OutMessage.writeDouble(name: String, value: Double?, defaultValue: Double?) {
  if (value != null && value != defaultValue) {
    writeDouble(name, value)
  }
}

fun OutMessage.writeDouble(name: String, value: Double) {
  beginArguments()
  writer.name(name).value(value)
}
