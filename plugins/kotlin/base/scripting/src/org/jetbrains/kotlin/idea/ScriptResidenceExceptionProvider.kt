// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings


open class ScriptResidenceExceptionProvider(
    private val suffix: String,
    private val supportedUnderSourceRoot: Boolean = false
) {
    open fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean =
        virtualFile.name.endsWith(suffix)

    fun isSupportedUnderSourceRoot(virtualFile: VirtualFile): Boolean =
        if (supportedUnderSourceRoot) isSupportedScriptExtension(virtualFile) else false
}

private val scriptResidenceExceptionProviders = listOf(
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

fun VirtualFile.hasUnknownScriptExt(): Boolean =
  scriptResidenceExceptionProviders.none { it.isSupportedScriptExtension(this) }

fun VirtualFile.hasNoExceptionsToBeUnderSourceRoot(): Boolean =
    scriptResidenceExceptionProviders.none { it.isSupportedUnderSourceRoot(this) }

fun LanguageFeature.isEnabled(module: Module?, project: Project): Boolean {
    val settings = module?.languageVersionSettings ?: project.languageVersionSettings
    return settings.supportsFeature(this)
}

fun compilerAllowsAnyScriptsInSourceRoots(project: Project): Boolean {
    val additionalSettings = KotlinCompilerSettings.getInstance(project).settings
    return additionalSettings.additionalArguments.contains("-Xallow-any-scripts-in-source-roots")
}