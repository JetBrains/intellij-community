// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k1.ucache.SdkId

class ScriptSdks(
  val sdks: Map<SdkId, Sdk?>,
  val nonIndexedClassRoots: Set<VirtualFile>,
  val nonIndexedSourceRoots: Set<VirtualFile>
) {
    fun rebuild(project: Project, remove: Sdk?): ScriptSdks {
        val builder = ScriptSdksBuilder(project, remove = remove)
        sdks.keys.forEach { id ->
            builder.addSdk(id)
        }
        return builder.build()
    }

    val first: Sdk? = sdks.values.firstOrNull()

    operator fun get(sdkId: SdkId) = sdks[sdkId]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptSdks
        return nonIndexedClassRoots == other.nonIndexedClassRoots &&
                nonIndexedSourceRoots == other.nonIndexedSourceRoots &&
                sdks.keys == other.sdks.keys && sdks.all { entry -> entry.value == other.sdks[entry.key] }
    }

    override fun hashCode(): Int {
        var result = nonIndexedClassRoots.hashCode()
        result = 31 * result + nonIndexedSourceRoots.hashCode()
        result = 31 * result + sdks.hashCode()
        return result
    }
}