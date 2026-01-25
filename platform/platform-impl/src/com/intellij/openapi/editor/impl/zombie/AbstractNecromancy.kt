// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.IOUtil
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon


abstract class AbstractNecromancy<Z : Zombie>(
  private val spellLevel: Int,
) : Necromancy<Z> {

  protected abstract fun Out.writeZombie(zombie: Z)

  protected abstract fun In.readZombie(): Z

  final override fun spellLevel(): Int {
    return spellLevel
  }

  final override fun buryZombie(grave: DataOutput, zombie: Z) {
    Out(grave).writeZombie(zombie)
  }

  final override fun exhumeZombie(grave: DataInput): Z {
    return In(grave).readZombie()
  }

  protected fun Out.writeInt(value: Int) {
    DataInputOutputUtil.writeINT(output, value)
  }

  protected fun In.readInt(): Int {
    return DataInputOutputUtil.readINT(input)
  }

  protected fun Out.writeLong(value: Long) {
    DataInputOutputUtil.writeLONG(output, value)
  }

  protected fun In.readLong(): Long {
    return DataInputOutputUtil.readLONG(input)
  }

  protected fun Out.writeString(value: String) {
    IOUtil.writeUTF(output, value)
  }

  protected fun In.readString(): String {
    return IOUtil.readUTF(input)
  }

  protected fun Out.writeBool(value: Boolean) {
    output.writeBoolean(value)
  }

  protected fun In.readBool(): Boolean {
    return input.readBoolean()
  }

  protected fun Out.writeIntOrNull(value: Int?) {
    writeNullable(value) {
      writeInt(it)
    }
  }

  protected fun In.readIntOrNull(): Int? {
    return readNullable {
      readInt()
    }
  }

  protected fun Out.writeLongOrNull(value: Long?) {
    writeNullable(value) {
      writeLong(it)
    }
  }

  protected fun In.readLongOrNull(): Long? {
    return readNullable {
      readLong()
    }
  }

  protected fun Out.writeStringOrNull(value: String?) {
    writeNullable(value) {
      writeString(it)
    }
  }

  protected fun In.readStringOrNull(): String? {
    return readNullable {
      readString()
    }
  }

  protected fun <T> Out.writeNullable(value: T?, write: (T) -> Unit) {
    if (value == null) {
      writeBool(false)
    } else {
      writeBool(true)
      write(value)
    }
  }

  protected fun <T> In.readNullable(read: () -> T): T? {
    return if (readBool()) {
      read()
    } else {
      null
    }
  }

  protected fun <T> Out.writeList(list: List<T>, write: (T) -> Unit) {
    writeInt(list.size)
    for (value in list) {
      write(value)
    }
  }

  protected fun <T> In.readList(read: () -> T): List<T> {
    val size = readInt()
    if (size == 0) {
      return emptyList()
    }
    return buildList(size) {
      repeat(size) {
        add(read())
      }
    }
  }

  protected fun Out.writeIconOrNull(icon: Icon?) {
    val bytes: ByteArray? = if (icon is CachedImageIcon) {
      try {
        icon.encodeToByteArray()
      } catch (e: Throwable) {
        logger<CachedImageIcon>().error(e)
        null
      }
    } else {
      null
    }
    if (bytes == null) {
      writeInt(0)
    } else {
      writeInt(bytes.size)
      output.write(bytes)
    }
  }

  protected fun In.readIconOrNull(): Icon? {
    val size = readInt()
    if (size != 0) {
      val byteArray = ByteArray(size)
      input.readFully(byteArray)
      try {
        return decodeCachedImageIconFromByteArray(byteArray)
      } catch (e: Throwable) {
        logger<CachedImageIcon>().error(e)
      }
    }
    return null
  }

  protected fun <E : Enum<E>> Out.writeEnum(value: Enum<E>) {
    val ordinal = value.ordinal
    writeInt(ordinal)
  }

  protected inline fun <reified E : Enum<E>> In.readEnum(): E {
    val ordinal = readInt()
    val values = enumValues<E>()
    if (0 <= ordinal && ordinal < values.size) {
      return values[ordinal]
    }
    error(
      "Invalid ordinal $ordinal " +
      "for enum ${E::class.java} " +
      "with values ${values.contentToString()}"
    )
  }

  protected class Out(val output: DataOutput)

  protected class In(val input: DataInput)
}
