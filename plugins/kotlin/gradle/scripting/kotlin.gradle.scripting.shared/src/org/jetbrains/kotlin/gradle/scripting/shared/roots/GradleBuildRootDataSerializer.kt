// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.gist.storage.GistStorage
import com.intellij.util.io.DataExternalizer
import org.jetbrains.kotlin.gradle.scripting.shared.GradleKotlinScriptConfigurationInputs
import org.jetbrains.kotlin.gradle.scripting.shared.LastModifiedFiles
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.idea.core.script.v1.readString
import org.jetbrains.kotlin.idea.core.script.v1.writeString
import java.io.DataInput
import java.io.DataOutput

private const val BINARY_FORMAT_VERSION = 1
private const val NO_TRACK_GIST_STAMP = 0

@Service(Service.Level.APP)
class GradleBuildRootDataSerializer {

    private val currentBuildRoot: ThreadLocal<VirtualFile> = ThreadLocal()

    private val buildRootDataGist =
        GistStorage.getInstance().newGist("GradleBuildRootData", BINARY_FORMAT_VERSION, Externalizer())

    fun read(buildRoot: VirtualFile): GradleBuildRootData? {
        currentBuildRoot.set(buildRoot)
        return buildRootDataGist.getGlobalData(buildRoot, NO_TRACK_GIST_STAMP).data()
    }

    fun write(buildRoot: VirtualFile, data: GradleBuildRootData?) {
        currentBuildRoot.set(buildRoot) // putGlobalData calls  Externalizer.read
        buildRootDataGist.putGlobalData(buildRoot, data, NO_TRACK_GIST_STAMP)
    }

    fun remove(buildRoot: VirtualFile) {
        write(buildRoot, null)
        LastModifiedFiles.remove(buildRoot)
    }

    inner class Externalizer: DataExternalizer<GradleBuildRootData> {
        override fun save(out: DataOutput, value: GradleBuildRootData) =
            writeKotlinDslScriptModels(out, value)

        override fun read(`in`: DataInput): GradleBuildRootData =
            readKotlinDslScriptModels(`in`, currentBuildRoot.get().path)
    }

    companion object {
        @JvmStatic
        fun getInstance(): GradleBuildRootDataSerializer = service()
    }
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
