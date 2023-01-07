// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.configuration

import com.intellij.openapi.vfs.VirtualFile


internal open class ScriptResidenceExceptionProvider(
    private val suffix: String,
    private val supportedUnderSourceRoot: Boolean = false
) {
    open fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean =
        virtualFile.name.endsWith(suffix)

    fun isSupportedUnderSourceRoot(virtualFile: VirtualFile): Boolean =
        if (supportedUnderSourceRoot) isSupportedScriptExtension(virtualFile) else false
}

internal val scriptResidenceExceptionProviders = listOf(
    ScriptResidenceExceptionProvider(".gradle.kts", true),
    ScriptResidenceExceptionProvider(".main.kts"),
    ScriptResidenceExceptionProvider(".space.kts"),
    ScriptResidenceExceptionProvider(".ws.kts", true),
    object : ScriptResidenceExceptionProvider(".teamcity.kts", true) {
        override fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean {
            if (!virtualFile.name.endsWith(".kts")) return false

            var parent = virtualFile.parent
            while (parent != null) {
                if (parent.isDirectory && parent.name == ".teamcity") {
                    return true
                }
                parent = parent.parent
            }

            return false
        }
    }
)