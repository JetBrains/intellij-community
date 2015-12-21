/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jsonProtocol

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.containers.isNullOrEmpty
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtf8Writer
import io.netty.buffer.ByteBufUtilEx
import org.jetbrains.io.JsonUtil
import java.io.IOException

open class OutMessage() {
  val buffer: ByteBuf = ByteBufAllocator.DEFAULT.heapBuffer()
  val writer = JsonWriter(ByteBufUtf8Writer(buffer))

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
    for (entry in value.entries) {
      writer.name(entry.key).value(entry.value)
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

  fun writeIntSet(name: String, value: TIntHashSet) {
    beginArguments()
    writer.name(name)
    writer.beginArray()
    value.forEach { value ->
      writer.value(value.toLong())
      true
    }
    writer.endArray()
  }

  fun writeIntList(name: String, value: TIntArrayList) {
      beginArguments()
      writer.name(name)
      writer.beginArray()
      for (i in 0..value.size() - 1) {
        writer.value(value.getQuick(i).toLong())
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
            throw RuntimeException(item.buffer.toString(CharsetToolkit.UTF8_CHARSET) + "\nparent:\n" + buffer.toString(CharsetToolkit.UTF8_CHARSET), e)
          }
          else {
            throw e
          }
        }

      }

      buffer.writeBytes(item.buffer)
    }
    writer.endArray()
  }

  fun writeStringList(name: String, value: Collection<String>) {
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

    beginArguments()
    prepareWriteRaw(this, name)

    if (!value.finalized) {
      value.close()
    }
    buffer.writeBytes(value.buffer)
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
    writer.name(name).value(value!!.toString())
  }

  companion object {
    @Throws(IOException::class)
    fun prepareWriteRaw(message: OutMessage, name: String) {
      message.writer.name(name).nullValue()
      val itemBuffer = message.buffer
      itemBuffer.writerIndex(itemBuffer.writerIndex() - "null".length)
    }

    fun doWriteRaw(message: OutMessage, rawValue: String) {
      ByteBufUtilEx.writeUtf8(message.buffer, rawValue)
    }
  }
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
  OutMessage.prepareWriteRaw(this, name)
  JsonUtil.escape(value, buffer)
}

fun OutMessage.writeInt(name: String, value: Int, defaultValue: Int) {
  if (value != defaultValue) {
    writeInt(name, value)
  }
}

fun OutMessage.writeInt(name: String, value: Int) {
  beginArguments()
  writer.name(name).value(value.toLong())
}

fun OutMessage.writeBoolean(name: String, value: Boolean, defaultValue: Boolean) {
  if (value != defaultValue) {
    writeBoolean(name, value)
  }
}

fun OutMessage.writeBoolean(name: String, value: Boolean) {
  beginArguments()
  writer.name(name).value(value)
}

fun OutMessage.writeDouble(name: String, value: Double, defaultValue: Double) {
  if (value != defaultValue) {
    writeDouble(name, value)
  }
}

fun OutMessage.writeDouble(name: String, value: Double) {
  beginArguments()
  writer.name(name).value(value)
}
