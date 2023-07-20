// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.utils.getKtFile
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsCache
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

class GradleScriptInfo(
    val buildRoot: Imported,
    scriptDefinition: ScriptDefinition?,
    val model: KotlinDslScriptModel,
    val project: Project?
) : ScriptClassRootsCache.LightScriptInfo(scriptDefinition) {

    override fun buildConfiguration(): ScriptCompilationConfigurationWrapper? {
        val scriptFile = File(model.file)
        val virtualFile = VfsUtil.findFile(scriptFile.toPath(), true) ?: return null
        val definition = definition ?: return null

        val configuration = definition.compilationConfiguration.with {
            buildRoot.javaHome?.let {
                jvm.jdkHome(it.toFile())
            }
            defaultImports(model.imports)
            dependencies(JvmDependency(model.classPath.map { File(it) }))
            ide.dependenciesSources(JvmDependency(model.sourcePath.map { File(it) }))
        }.adjustByDefinition(definition)

        return ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(VirtualFileScriptSource(virtualFile), configuration)
            .also { configuration.refineIfNeeded(virtualFile) }
    }

    private fun ScriptCompilationConfiguration.refineIfNeeded(
        virtualFile: VirtualFile
    ) {
        val ktFile = project?.getKtFile(virtualFile, null) ?: return
        val scriptDefinition = ktFile.findScriptDefinition()
            ?: error("Couldn't find script definition for ${ktFile.virtualFilePath}")

        if (scriptDefinition.isDefinedViaModernApi()) { // refinement of the previous version is very inefficient (takes too much time)
            refineScriptCompilationConfiguration(VirtualFileScriptSource(virtualFile), scriptDefinition, project, this)
        }
    }

    private fun ScriptDefinition.isDefinedViaModernApi() =
        asLegacyOrNull<KotlinScriptDefinition>() == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GradleScriptInfo

        if (buildRoot.pathPrefix != other.buildRoot.pathPrefix) return false
        if (model != other.model) return false
        if (definition != other.definition) return false

        return true
    }

    override fun hashCode(): Int {
        var result = buildRoot.pathPrefix.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + definition.hashCode()
        return result
    }
}
