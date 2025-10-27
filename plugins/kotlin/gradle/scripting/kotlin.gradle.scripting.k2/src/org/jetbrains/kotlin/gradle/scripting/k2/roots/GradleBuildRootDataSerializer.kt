// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.roots

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.io.DataExternalizer
import org.jetbrains.kotlin.gradle.scripting.shared.roots.AbstractGradleBuildRootDataSerializer
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.jetbrains.kotlin.gradle.scripting.shared.roots.StringsPool
import java.io.DataInput
import java.io.DataOutput

class GradleBuildRootDataSerializer : AbstractGradleBuildRootDataSerializer() {
    override val externalizer: DataExternalizer<GradleBuildRootData>
        get() = object : DataExternalizer<GradleBuildRootData> {
        override fun save(out: DataOutput, value: GradleBuildRootData) =
            writeKotlinDslScriptModels(out, value)

        override fun read(`in`: DataInput): GradleBuildRootData =
            readKotlinDslScriptModels(`in`)
    }

    companion object {
        @IntellijInternalApi
        fun writeKotlinDslScriptModels(output: DataOutput, data: GradleBuildRootData) {
            val strings = StringsPool.writer(output)
            strings.addStrings(data.projectRoots)
            strings.addString(data.gradleHome)
            strings.addString(data.javaHome)
            strings.writeHeader()
            output.writeLong(data.importTs)
            strings.writeStringIds(data.projectRoots)
            strings.writeStringId(data.gradleHome)
            strings.writeStringId(data.javaHome)
        }

        @IntellijInternalApi
        fun readKotlinDslScriptModels(input: DataInput): GradleBuildRootData {
            val strings = StringsPool.reader(input)

            val importTs = input.readLong()
            val projectRoots = strings.readStrings()
            val gradleHome = strings.readString()
            val javaHome = strings.readNullableString()

            return GradleBuildRootData(importTs, projectRoots, gradleHome, javaHome, listOf())
        }
    }
}