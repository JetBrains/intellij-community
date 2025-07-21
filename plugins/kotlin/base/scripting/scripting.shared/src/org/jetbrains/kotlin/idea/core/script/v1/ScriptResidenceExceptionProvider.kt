// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.v1

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.isStandalone


open class ScriptResidenceExceptionProvider(
    private val suffix: String,
    private val supportedUnderSourceRoot: Boolean = false
) {
    open fun isSupportedScriptExtension(virtualFile: VirtualFile): Boolean =
        virtualFile.name.endsWith(suffix)

    fun isSupportedUnderSourceRoot(virtualFile: VirtualFile): Boolean =
        if (supportedUnderSourceRoot) isSupportedScriptExtension(virtualFile) else false
}

private val scriptResidenceExceptionProviders: List<ScriptResidenceExceptionProvider> = listOf(
    ScriptResidenceExceptionProvider(".gradle.kts", true),
    ScriptResidenceExceptionProvider(".main.kts"),
    ScriptResidenceExceptionProvider(".space.kts"),
    ScriptResidenceExceptionProvider(".inspection.kts"),
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

@ApiStatus.Internal
fun compilerAllowsAnyScriptsInSourceRoots(project: Project): Boolean {
    val additionalSettings = KotlinCompilerSettings.getInstance(project).settings
    return additionalSettings.additionalArguments.contains("-Xallow-any-scripts-in-source-roots")
}

@ApiStatus.Internal
fun VirtualFile.isRunnableKotlinScript(project: Project): Boolean {
    if (nameSequence.endsWith(".gradle.kts")) return false
    return isStandaloneKotlinScript(project)
}

@ApiStatus.Internal
fun VirtualFile.isStandaloneKotlinScript(project: Project): Boolean {
    val ktFile = (toPsiFile(project) as? KtFile)?.takeIf(KtFile::isScript) ?: return false
    val scriptDefinition = ScriptDefinitionProvider.getInstance(project)?.findDefinition(KtFileScriptSource(ktFile))
        ?: return false
    return scriptDefinition.compilationConfiguration[ScriptCompilationConfiguration.isStandalone] == true
}
