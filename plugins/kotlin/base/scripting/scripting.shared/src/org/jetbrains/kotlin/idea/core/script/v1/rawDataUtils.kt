// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq
import com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.*

fun DataInput.readStringList(): List<String> = readSeq(this) { readString() }

fun DataInput.readString(): String = readUTF(this)

fun DataInput.readFile() = File(readUTF(this))

fun DataOutput.writeString(string: String) = writeUTF(this, string)

fun DataOutput.writeStringList(iterable: Iterable<String>) = writeSeq(this, iterable.toList()) { writeString(it) }

fun <T : Any> DataOutput.writeNullable(nullable: T?, writeT: DataOutput.(T) -> Unit) {
    writeBoolean(nullable != null)
    nullable?.let { writeT(it) }
}

fun <T : Any> DataInput.readNullable(readT: DataInput.() -> T): T? {
    val hasValue = readBoolean()
    return if (hasValue) readT() else null
}

inline fun <reified T : Any> DataOutput.writeObject(obj: T) {
    val os = ByteArrayOutputStream()
    ObjectOutputStream(os).use { oos ->
        oos.writeObject(obj)
    }
    val bytes = os.toByteArray()
    writeInt(bytes.size)
    write(bytes)
}

inline fun <reified T : Any> DataInput.readObject(): T {
    val size = readInt()
    val bytes = ByteArray(size)

    repeat(size) { bytes[it] = readByte() }

    val bis = ByteArrayInputStream(bytes)
    return ObjectInputStream(bis).use { ois ->
        ois.readObject() as T
    }
}