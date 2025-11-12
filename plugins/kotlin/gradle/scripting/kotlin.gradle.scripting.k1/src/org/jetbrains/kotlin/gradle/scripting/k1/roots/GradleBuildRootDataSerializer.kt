// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k1.roots

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.io.DataExternalizer
import org.jetbrains.kotlin.gradle.scripting.shared.roots.AbstractGradleBuildRootDataSerializer
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.jetbrains.kotlin.gradle.scripting.shared.roots.StringsPool
import org.jetbrains.kotlin.gradle.scripting.shared.roots.writeList
import org.jetbrains.kotlin.idea.core.script.v1.writeString
import java.io.DataInput
import java.io.DataOutput

class GradleBuildRootDataSerializer : AbstractGradleBuildRootDataSerializer() {
    override fun getExternalizer(): DataExternalizer<GradleBuildRootData> = object : DataExternalizer<GradleBuildRootData> {
        override fun save(out: DataOutput, value: GradleBuildRootData) = writeKotlinDslScriptModels(out, value)
        override fun read(`in`: DataInput): GradleBuildRootData = readKotlinDslScriptModels(`in`, currentBuildRoot.get().path)
    }

    companion object {
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
    }

}