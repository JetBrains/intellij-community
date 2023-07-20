// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.VirtualFileGist
import com.intellij.util.io.DataExternalizer
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.idea.gradle.scripting.LastModifiedFiles
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleKotlinScriptConfigurationInputs
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModel
import java.io.DataInput
import java.io.DataOutput

internal object GradleBuildRootDataSerializer {

    private val currentBuildRoot: ThreadLocal<VirtualFile> = ThreadLocal()
    private val currentData: ThreadLocal<GradleBuildRootData> = ThreadLocal()

    fun read(buildRoot: VirtualFile): GradleBuildRootData? {
        currentBuildRoot.set(buildRoot)
        return runReadAction { buildRootGist.getFileData(null, buildRoot) }
    }

    fun write(buildRoot: VirtualFile, data: GradleBuildRootData?) {
        GistManager.getInstance().invalidateData(buildRoot)
        if (data == null) return

        currentBuildRoot.set(buildRoot)
        currentData.set(data)

        runReadAction { buildRootGist.getFileData(null, buildRoot) }
    }

    fun remove(buildRoot: VirtualFile) {
        write(buildRoot, null)
        LastModifiedFiles.remove(buildRoot)
    }

    /*
        The idea to utilize VirtualFileGist is dictated by the need to avoid using VFS attributes.
        By the moment of this change there is no good alternative - VirtualFileGist isn't designed to be a key-value storage, its API
        isn't designed for the purposes of this class. Hence, thread-locals and data invalidation for write method.
        Once a better solution exists it should be applied instead.
     */
    private val buildRootGist: VirtualFileGist<GradleBuildRootData> = GistManager.getInstance().newVirtualFileGist(
        "kotlin-dsl-script-models",
        1,
        object : DataExternalizer<GradleBuildRootData> {
            override fun save(out: DataOutput, value: GradleBuildRootData) {
                writeKotlinDslScriptModels(out, value)
            }

            override fun read(input: DataInput): GradleBuildRootData {
                return readKotlinDslScriptModels(input, currentBuildRoot.get().path)
            }
        },
    ) { _, _ -> currentData.get() }

}

@IntellijInternalApi
fun writeKotlinDslScriptModels(output: DataOutput, data: GradleBuildRootData) {
    val strings = StringsPool.writer(output)
    strings.addStrings(data.projectRoots)
    strings.addString(data.gradleHome)
    strings.addString(data.javaHome)
    data.models.forEach {
        strings.addString(it.file)
        strings.addStrings(it.classPath)
        strings.addStrings(it.sourcePath)
        strings.addStrings(it.imports)
    }
    strings.writeHeader()
    output.writeLong(data.importTs)
    strings.writeStringIds(data.projectRoots)
    strings.writeStringId(data.gradleHome)
    strings.writeStringId(data.javaHome)
    output.writeList(data.models) {
        strings.writeStringId(it.file)
        output.writeString(it.inputs.sections)
        output.writeLong(it.inputs.lastModifiedTs)
        strings.writeStringIds(it.classPath)
        strings.writeStringIds(it.sourcePath)
        strings.writeStringIds(it.imports)
    }
}

@IntellijInternalApi
fun readKotlinDslScriptModels(input: DataInput, buildRoot: String): GradleBuildRootData {
    val strings = StringsPool.reader(input)

    val importTs = input.readLong()
    val projectRoots = strings.readStrings()
    val gradleHome = strings.readString()
    val javaHome = strings.readNullableString()
    val models = input.readList {
        KotlinDslScriptModel(
            strings.readString(),
            GradleKotlinScriptConfigurationInputs(input.readString(), input.readLong(), buildRoot),
            strings.readStrings(),
            strings.readStrings(),
            strings.readStrings(),
            listOf()
        )
    }

    return GradleBuildRootData(importTs, projectRoots, gradleHome, javaHome, models)
}

private object StringsPool {
    fun writer(output: DataOutput) = Writer(output)

    class Writer(val output: DataOutput) {
        var freeze = false
        val ids = mutableMapOf<String, Int>()

        fun getStringId(string: String) = ids.getOrPut(string) {
            check(!freeze)
            ids.size
        }

        fun addString(string: String?) {
            getStringId(string ?: "null")
        }

        fun addStrings(list: Collection<String>) {
            list.forEach { addString(it) }
        }

        fun writeHeader() {
            freeze = true

            output.writeInt(ids.size)

            // sort for optimal performance and compression
            ids.keys.sorted().forEachIndexed { index, s ->
                ids[s] = index
                output.writeString(s)
            }
        }

        fun writeStringId(it: String?) {
            output.writeInt(getStringId(it ?: "null"))
        }

        fun writeStringIds(strings: Collection<String>) {
            output.writeInt(strings.size)
            strings.forEach {
                writeStringId(it)
            }
        }
    }

    fun reader(input: DataInput): Reader {
        val strings = input.readList { input.readString() }
        return Reader(input, strings)
    }

    class Reader(val input: DataInput, val strings: List<String>) {
        fun getString(id: Int) = strings[id]

        fun readString() = getString(input.readInt())
        fun readNullableString(): String? {
            val string = getString(input.readInt())
            if (string == "null") return null
            return string
        }

        fun readStrings(): List<String> = input.readList { readString() }
    }
}

private inline fun <T> DataOutput.writeList(list: Collection<T>, write: (T) -> Unit) {
    writeInt(list.size)
    list.forEach { write(it) }
}

private inline fun <T> DataInput.readList(read: () -> T): List<T> {
    val n = readInt()
    val result = ArrayList<T>(n)
    repeat(n) {
        result.add(read())
    }
    return result
}
