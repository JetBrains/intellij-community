// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.k2.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.k2.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.*
import kotlin.script.templates.standard.ScriptTemplateWithArgs

val scriptClassPath: List<File> = listOf(
    KotlinArtifacts.kotlinScriptRuntime,
    KotlinArtifacts.kotlinStdlib,
    KotlinArtifacts.kotlinReflect
)

class BundledScriptDefinitionSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition> = sequenceOf(project.defaultDefinition)
}

private fun Project.javaHomePath(): File? {
    val sdk = ProjectRootManager.getInstance(this)?.projectSdk?.takeIf { it.sdkType is JavaSdkType }
    val anyJdk = PathUtilEx.getAnyJdk(this)
    return (sdk ?: anyJdk)?.homePath?.let { File(it) }
}

val Project.defaultDefinition: ScriptDefinition
    get() {
        val project = this
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                project.javaHomePath()?.let {
                    jvm.jdkHome(it)
                }
                dependencies(JvmDependency(scriptClassPath))
                displayName("Default Kotlin Script")
                hostConfiguration(defaultJvmScriptingHostConfiguration)
                ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
                ide.kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                    id = "default-kts"
                    title = ".kts"
                })
            }
        )

        return BundledScriptDefinition(compilationConfiguration, evaluationConfiguration)
    }

private const val BUNDLED_SCRIPT_DEFINITION_ID = "ideBundledScriptDefinition"

class BundledScriptDefinition(
    compilationConfiguration: ScriptCompilationConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?
) : ScriptDefinition.FromConfigurations(
    defaultJvmScriptingHostConfiguration,
    compilationConfiguration,
    evaluationConfiguration
) {
    override val canDefinitionBeSwitchedOff: Boolean = false
    override val isDefault: Boolean = true
    override val definitionId: String
        get() = BUNDLED_SCRIPT_DEFINITION_ID
}

@Suppress("unused")
@KotlinScript(
    displayName = "KotlinScratchScript",
    fileExtension = "kts",
    compilationConfiguration = KotlinScratchCompilationConfiguration::class,
    hostConfiguration = KotlinScratchHostConfiguration::class
)
abstract class KotlinScratchScript()

private class KotlinScratchCompilationConfiguration() : ScriptCompilationConfiguration(
    {
        displayName("Kotlin Scratch")
        explainField(SCRATCH_EXPLAIN_VARIABLE_NAME)
    })

private class KotlinScratchHostConfiguration : ScriptingHostConfiguration(
    {
        getScriptingClass(JvmGetScriptingClass())
    })


private const val SCRATCH_EXPLAIN_VARIABLE_NAME: String = "\$\$explain"
