// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION", "IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.PathUtil
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.k2.definitions.KotlinScriptDefinitionsProviderId
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.shared.definition.jdkSupplier
import org.jetbrains.kotlin.idea.core.script.shared.definition.scriptClassPath
import org.jetbrains.kotlin.idea.core.script.v1.ScratchFileOptionsByFile

class KotlinScratchDefinitionProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String = KotlinScriptDefinitionsProviderId.KOTLIN_SCRATCH.id

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>,
    ): List<ScriptDefinition> = listOf(
        ScriptDefinition(createCompilationConfiguration(), ScriptEvaluationConfiguration.Default)
    )

    private fun createCompilationConfiguration(): ScriptCompilationConfiguration = ScriptCompilationConfiguration(
        listOf(KotlinScratchCompilationConfiguration)
    ) {
        dependencies(JvmDependency(scriptClassPath))
        displayName("Kotlin Scratch")
        hostConfiguration(defaultJvmScriptingHostConfiguration)
        ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
        filePathPattern(scratchPathPattern())
        ide.jdkSupplier { virtualFile ->
            val jdkHome = scratchModuleSdkHome(project, virtualFile)
                ?: ScratchFileOptionsByFile[project, virtualFile].selectedJdkHome
                ?: defaultScratchJavaHome
            jdkHome?.takeIf { it.isNotBlank() }?.let(::File)
        }
    }

    private fun scratchPathPattern(): String {
        val root = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance())
        val vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtilRt.toSystemIndependentName(root))
            ?: return ".*/${Regex.escape(PathUtil.getFileName(root))}/.*"

        return "${Regex.escape(vfsRoot.path)}/.*"
    }
}
