// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq
import com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.util.ObjectUtils
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractFileAttributePropertyService<T: Any>(
    name: String,
    version: Int,
    private val default: T? = null,
    private val read: DataInputStream.() -> T,
    private val write: DataOutputStream.(T) -> Unit
): Disposable {
    private val attribute = attribute(name, version)
    private val cache = ConcurrentHashMap<VirtualFile, Any?>()

    private fun computeValue(file: VirtualFile): T? {
        if (file !is VirtualFileWithId || !file.isValid) return null

        return attribute.readFileAttribute(file)?.use { input ->
            try {
                input.readNullable {
                    read(input)
                }
            } catch (e: Throwable) {
                Logger.getInstance("#org.jetbrains.kotlin.idea.core.util.FileAttributeProperty")
                    .warn("Unable to read attribute from $file", e)
                null
            }
        } ?: default
    }

    operator fun set(file: VirtualFile, newValue: T?) {
        if (file !is VirtualFileWithId || !file.isValid) return

        attribute.writeFileAttribute(file).use { output ->
            output.writeNullable(newValue) { value ->
                write(output, value)
            }
        }

        // clear cache
        cache.remove(file)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(file: VirtualFile): T? =
        cache.computeIfAbsent(file) {
            computeValue(file) ?: ObjectUtils.NULL
        }.takeIf { it != ObjectUtils.NULL } as T?

    override fun dispose() {
        cache.clear()
    }

    companion object {
        @JvmStatic
        private val attributes = mutableMapOf<String, FileAttribute>()

        @JvmStatic
        private fun attribute(name: String, version: Int): FileAttribute {
            val attribute = synchronized(attributes) {
                attributes.computeIfAbsent(name) {
                    FileAttribute(name, version, false)
                }
            }
            check(attribute.version == version) {
                "FileAttribute version $version differs with existed one ${attribute.version}"
            }
            return attribute
        }
    }

}

fun DataInput.readStringList(): List<String> = readSeq(this) { readString() }
fun DataInput.readFileList(): List<File> = readSeq(this) { readFile() }
fun DataInput.readString(): String = readUTF(this)
fun DataInput.readFile() = File(readUTF(this))

fun DataOutput.writeFileList(iterable: Iterable<File>) = writeSeq(this, iterable.toList()) { writeFile(it) }
fun DataOutput.writeFile(it: File) = writeString(it.canonicalPath)
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

inline fun <reified T : Any> DataOutputStream.writeObject(obj: T) {
    val os = ByteArrayOutputStream()
    ObjectOutputStream(os).use { oos ->
        oos.writeObject(obj)
    }
    val bytes = os.toByteArray()
    writeInt(bytes.size)
    write(bytes)
}

inline fun <reified T : Any> DataInputStream.readObject(): T {
    val size = readInt()
    val bytes = ByteArray(size)
    read(bytes, 0, size)
    val bis = ByteArrayInputStream(bytes)
    return ObjectInputStream(bis).use { ois ->
        ois.readObject() as T
    }
}