// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.ObjectUtils
import com.intellij.util.gist.storage.GistStorage
import com.intellij.util.io.ByteSequenceDataExternalizer
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.DataOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val NO_TRACK_GIST_STAMP = 0

abstract class AbstractFileGistService<T: Any>(
  name: String,
  version: Int,
  private val default: T? = null,
  private val read: DataInput.() -> T,
  private val write: DataOutput.(T) -> Unit
): Disposable {

    private val gist = GistStorage.getInstance().newGist(name, version, ByteSequenceDataExternalizer.INSTANCE)

    private val cache = ConcurrentHashMap<VirtualFile, Any?>()

    private fun computeValue(file: VirtualFile): T? {
        if (file !is VirtualFileWithId || !file.isValid) return null
        val gistData = gist.getGlobalData(file, NO_TRACK_GIST_STAMP).data()
        return gistData?.let { (it.toInputStream() as DataInput).read() }
            ?: default
    }

    operator fun set(file: VirtualFile, newValue: T?) {
        if (file !is VirtualFileWithId || !file.isValid) return

        val sequence = newValue?.let { value ->
            val byteArrayOutputStream = ByteArrayOutputStream()
            DataOutputStream(byteArrayOutputStream).use {
                it.write(value)
                ByteArraySequence(byteArrayOutputStream.toByteArray())
            }
        }

        gist.putGlobalData(file, sequence, NO_TRACK_GIST_STAMP)
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
}