// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.script.IdeConsoleRootType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib

val scriptClassPath = listOf(
    KotlinArtifacts.kotlinScriptRuntime,
    KotlinArtifacts.kotlinStdlib,
    KotlinArtifacts.kotlinReflect
)

class BundledScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition> = sequenceOf(project.defaultScratchDefinition, project.defaultDefinition)
}

val Project.defaultScratchDefinition: ScriptDefinition
    get() {
        val compilationConfiguration = ScriptCompilationConfiguration.Default.with {
            javaHomePath()?.let {
                jvm.jdkHome(it)
            }
            dependencies(JvmDependency(scriptClassPath + scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true)))
            displayName("Bundled Script Definition")
            hostConfiguration(defaultJvmScriptingHostConfiguration)
        }

        return object : BundledScriptDefinition(compilationConfiguration) {
            override fun isScript(script: SourceCode): Boolean =
                when (script) {
                    is VirtualFileScriptSource -> ScratchFileService.getInstance().getRootType(script.virtualFile) is IdeConsoleRootType
                    else -> false
                }
        }
    }

private fun Project.javaHomePath(): File? {
    val sdk = ProjectRootManager.getInstance(this)?.projectSdk?.takeIf { it.sdkType is JavaSdkType }
    val anyJdk = PathUtilEx.getAnyJdk(this)
    return (sdk ?: anyJdk)?.homePath?.let { File(it) }
}

val Project.defaultDefinition: ScriptDefinition
    get() {
        val compilationConfiguration = ScriptCompilationConfiguration.Default.with {
            javaHomePath()?.let {
                jvm.jdkHome(it)
            }
            dependencies(JvmDependency(scriptClassPath))
            displayName("Bundled Script Definition")
            hostConfiguration(defaultJvmScriptingHostConfiguration)
        }

        return BundledScriptDefinition(compilationConfiguration)
    }

open class BundledScriptDefinition(
    compilationConfiguration: ScriptCompilationConfiguration,
) : ScriptDefinition.FromConfigurations(
    defaultJvmScriptingHostConfiguration,
    compilationConfiguration,
    ScriptEvaluationConfiguration.Default
) {
    override val canDefinitionBeSwitchedOff: Boolean = false
    override val isDefault: Boolean = true
}
