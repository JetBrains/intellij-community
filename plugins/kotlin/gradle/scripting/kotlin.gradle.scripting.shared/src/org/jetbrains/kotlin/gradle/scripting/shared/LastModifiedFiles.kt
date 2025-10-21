// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import org.jetbrains.kotlin.idea.core.script.v1.*
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream

/**
 * Optimized collection for storing last modified files with ability to
 * get time of last modified file expect given one ([lastModifiedTimeStampExcept]).
 *
 * This is required since Gradle scripts configurations should be updated on
 * each other script changes (but not on the given script changes itself).
 *
 * Actually works by storing two last timestamps with the set of files modified at this times.
 */
class LastModifiedFiles(
    private var last: SimultaneouslyChangedFiles = SimultaneouslyChangedFiles(),
    private var previous: SimultaneouslyChangedFiles = SimultaneouslyChangedFiles()
) {
    init {
        previous.fileIds.removeAll(last.fileIds)
        if (previous.fileIds.isEmpty()) previous = SimultaneouslyChangedFiles()
    }

    class SimultaneouslyChangedFiles(
        val ts: Long = Long.MIN_VALUE,
        val fileIds: MutableSet<String> = mutableSetOf()
    ) {
        override fun toString(): String {
            return "SimultaneouslyChangedFiles(ts=$ts, fileIds=$fileIds)"
        }
    }

    @Synchronized
    fun fileChanged(ts: Long, fileId: String) {
        when {
            ts > last.ts -> {
                val prevPrev = previous
                previous = last
                previous.fileIds.remove(fileId)
                if (previous.fileIds.isEmpty()) previous = prevPrev
                last = SimultaneouslyChangedFiles(ts, hashSetOf(fileId))
            }
            ts == last.ts -> last.fileIds.add(fileId)
            ts == previous.ts -> previous.fileIds.add(fileId)
        }
    }

    @Synchronized
    fun lastModifiedTimeStampExcept(fileId: String): Long = when {
        last.fileIds.size == 1 && last.fileIds.contains(fileId) -> previous.ts
        else -> last.ts
    }

    override fun toString(): String {
        return "LastModifiedFiles(last=$last, previous=$previous)"
    }

    companion object {
        private val fileAttribute = FileAttribute("last-modified-files", 1, false)

        fun read(buildRoot: VirtualFile): LastModifiedFiles? {
            try {
                return fileAttribute.readFileAttribute(buildRoot)?.use {
                    readLastModifiedFiles(it)
                }
            } catch (e: Exception) {
              scriptingErrorLog("Cannot read data for buildRoot=$buildRoot from file attributes", e)
                return null
            }
        }

        @IntellijInternalApi
        fun readLastModifiedFiles(it: DataInputStream) = it.readNullable {
            LastModifiedFiles(readSCF(it), readSCF(it))
        }

        fun write(buildRoot: VirtualFile, data: LastModifiedFiles?) {
            try {
                fileAttribute.writeFileAttribute(buildRoot).use {
                    writeLastModifiedFiles(it, data)
                }
            } catch (e: Exception) {
              scriptingErrorLog("Cannot store data=$data for buildRoot=$buildRoot to file attributes", e)

                fileAttribute.writeFileAttribute(buildRoot).use {
                    writeLastModifiedFiles(it, null)
                }
            }
        }

        @IntellijInternalApi
        fun writeLastModifiedFiles(it: DataOutputStream, data: LastModifiedFiles?) {
            it.writeNullable(data) { data ->
                writeSCF(data.last)
                writeSCF(data.previous)
            }
        }

        fun remove(buildRoot: VirtualFile) {
            write(buildRoot, null)
        }

        private fun readSCF(it: DataInputStream) = SimultaneouslyChangedFiles(it.readLong(), it.readStringList().toMutableSet())

        private fun DataOutput.writeSCF(last: SimultaneouslyChangedFiles) {
            writeLong(last.ts)
            writeStringList(last.fileIds.toList())
        }
    }
}