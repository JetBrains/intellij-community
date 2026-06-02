// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared.definition

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.io.File
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.templates.standard.ScriptTemplateWithArgs

val scriptClassPath: List<File> = listOf(
    KotlinArtifacts.kotlinScriptRuntime,
    KotlinArtifacts.kotlinStdlib,
    KotlinArtifacts.kotlinReflect
)

val Project.javaHomePath: File?
    get() {
        val sdk = ProjectRootManager.getInstance(this)?.projectSdk?.takeIf { it.sdkType is JavaSdkType }
        val anyJdk = PathUtilEx.getAnyJdk(this)
        return (sdk ?: anyJdk)?.homePath?.let { File(it) }
    }

fun getBundledScriptDefinition(project: Project) = createScriptDefinitionFromTemplate(
    KotlinType(ScriptTemplateWithArgs::class),
    defaultJvmScriptingHostConfiguration,
    compilation = {
        dependencies(JvmDependency(scriptClassPath))
        displayName("Kotlin Script")
        hostConfiguration(defaultJvmScriptingHostConfiguration)
        ide.dependenciesSources(JvmDependency(KotlinArtifacts.kotlinStdlibSources))
        ide.jdkSupplier { project.javaHomePath }
    }
)

val IdeScriptCompilationConfigurationKeys.jdkSupplier: PropertiesCollection.Key<(VirtualFile) -> File?> by PropertiesCollection.key(
    getDefaultValue = {
        { get(ScriptCompilationConfiguration.jvm.jdkHome) }
    }
)

class BundledScriptDefinition(
    compilationConfiguration: ScriptCompilationConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?
) : ScriptDefinition.FromConfigurations(
    defaultJvmScriptingHostConfiguration,
    compilationConfiguration,
    evaluationConfiguration
) {
    init {
        order = Integer.MAX_VALUE
    }

    override val canDefinitionBeSwitchedOff: Boolean = false
    override val isDefault: Boolean = true
    override val definitionId: String
        get() = "ideBundledScriptDefinition"
}

