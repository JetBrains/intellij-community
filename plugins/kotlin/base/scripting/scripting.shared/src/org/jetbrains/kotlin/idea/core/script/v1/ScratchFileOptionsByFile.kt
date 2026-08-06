// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.ObjectUtils
import com.intellij.util.gist.storage.GistStorage
import com.intellij.util.io.ByteSequenceDataExternalizer
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile.Companion.set
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.DataOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val NO_TRACK_GIST_STAMP = 0

@Service(Service.Level.PROJECT)
class ScratchFileOptionsByFile : Disposable {
    private val name = "kotlin-scratch-file-options"
    private val version = 2

    private val read: DataInput.() -> ScratchFileOptions = {
        ScratchFileOptions(
            isRepl = readBoolean(),
            isMakeBeforeRun = readBoolean(),
            isInteractiveMode = readBoolean(),
            isExplainEnabled = readBoolean(),
            selectedJdkHome = readNullable { readString() },
            selectedModule = readNullable { readString() },
        )
    }
    private val write: DataOutput.(ScratchFileOptions) -> Unit = { options ->
        writeBoolean(options.isRepl)
        writeBoolean(options.isMakeBeforeRun)
        writeBoolean(options.isInteractiveMode)
        writeBoolean(options.isExplainEnabled)
        writeNullable(options.selectedJdkHome) { writeString(it) }
        writeNullable(options.selectedModule) { writeString(it) }
    }

    companion object {
        @JvmStatic
        operator fun get(project: Project, file: VirtualFile): ScratchFileOptions {
            return project.service<ScratchFileOptionsByFile>()[file] ?: ScratchFileOptions()
        }

        @JvmStatic
        operator fun set(project: Project, file: VirtualFile, newValue: ScratchFileOptions?) {
            project.service<ScratchFileOptionsByFile>()[file] = newValue
        }

        fun update(project: Project, file: VirtualFile, update: ScratchFileOptions.() -> ScratchFileOptions) {
            ScratchFileOptionsByFile[project, file] = ScratchFileOptionsByFile[project, file].update()
        }
    }

    private val gist = GistStorage.getInstance().newGist(name, version, ByteSequenceDataExternalizer.INSTANCE)

    private val cache = ConcurrentHashMap<VirtualFile, Any?>()

    private fun computeValue(file: VirtualFile): ScratchFileOptions? {
        if (file !is VirtualFileWithId || !file.isValid) return null
        val gistData = gist.getGlobalData(file, NO_TRACK_GIST_STAMP).data()
        return gistData?.let { (it.toInputStream() as DataInput).read() }
    }

    operator fun set(file: VirtualFile, newValue: ScratchFileOptions?) {
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
    operator fun get(file: VirtualFile): ScratchFileOptions? =
        cache.computeIfAbsent(file) {
            computeValue(file) ?: ObjectUtils.NULL
        }.takeIf { it != ObjectUtils.NULL } as ScratchFileOptions?

    override fun dispose() {
        cache.clear()
    }
}

data class ScratchFileOptions(
    val isRepl: Boolean = false,
    val isMakeBeforeRun: Boolean = true,
    val isInteractiveMode: Boolean = false,
    val isExplainEnabled: Boolean = true,
    val selectedJdkHome: String? = null,
    val selectedModule: String? = null,
)
