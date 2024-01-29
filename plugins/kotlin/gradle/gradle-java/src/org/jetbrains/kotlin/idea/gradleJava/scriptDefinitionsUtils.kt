// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.kotlin.idea.gradle.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.gradle.scripting.GradleKotlinScriptDefinitionWrapper
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslSyncListener
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinitionAdapterFromNewAPIBase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.reflect.KClass
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptReport
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

private val kotlinStdLibSelector = Regex("^(kotlin-compiler-embeddable|kotlin-stdlib)-(\\d+\\.\\d+).*\\.jar\$")

fun loadGradleDefinitions(workingDir: String, gradleHome: String?, javaHome: String?, project: Project): List<ScriptDefinition> {
    try {
        val (templateClasspath, additionalClassPath) = getFullDefinitionsClasspath(gradleHome)

        val kotlinDslTemplates = ArrayList<ScriptDefinition>()

        loadGradleTemplates(
            workingDir,
            templateClass = "org.gradle.kotlin.dsl.KotlinInitScript",
            gradleHome = gradleHome,
            javaHome = javaHome,
            templateClasspath = templateClasspath,
            additionalClassPath = additionalClassPath,
            project
        ).let { kotlinDslTemplates.addAll(it) }

        loadGradleTemplates(
            workingDir,
            templateClass = "org.gradle.kotlin.dsl.KotlinSettingsScript",
            gradleHome = gradleHome,
            javaHome = javaHome,
            templateClasspath = templateClasspath,
            additionalClassPath = additionalClassPath,
            project
        ).let { kotlinDslTemplates.addAll(it) }

        // KotlinBuildScript should be last because it has wide scriptFilePattern
        loadGradleTemplates(
            workingDir,
            templateClass = "org.gradle.kotlin.dsl.KotlinBuildScript",
            gradleHome = gradleHome,
            javaHome = javaHome,
            templateClasspath = templateClasspath,
            additionalClassPath = additionalClassPath,
            project
        ).let { kotlinDslTemplates.addAll(it) }


        if (kotlinDslTemplates.isNotEmpty()) {
            return kotlinDslTemplates.distinct()
        }
    } catch (t: Throwable) {
        // TODO: review exception handling

        if (t is IllegalStateException) {
            scriptingInfoLog("IllegalStateException loading gradle script templates: ${t.message}")
        } else {
            scriptingDebugLog { "error loading gradle script templates ${t.message}" }
        }

        return listOf(ErrorGradleScriptDefinition(project, t.message))
    }

    return listOf(ErrorGradleScriptDefinition(project))

}


// TODO: refactor - minimize
class ErrorGradleScriptDefinition(project: Project, message: String? = null) :
    ScriptDefinition.FromLegacy(
        ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration),
        LegacyDefinition(project, message),
        emptyList()
    ) {

    private class LegacyDefinition(project: Project, message: String?) : KotlinScriptDefinitionAdapterFromNewAPIBase() {
        companion object {
            private const val KOTLIN_DSL_SCRIPT_EXTENSION = "gradle.kts"
        }

        override val name: String get() = KotlinIdeaGradleBundle.message("text.default.kotlin.gradle.script")
        override val fileExtension: String = KOTLIN_DSL_SCRIPT_EXTENSION

        override val scriptCompilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration.Default
        override val hostConfiguration: ScriptingHostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration)
        override val baseClass: KClass<*> = ScriptTemplateWithArgs::class

        override val dependencyResolver: DependenciesResolver =
            ErrorScriptDependenciesResolver(project, message)

        override fun toString(): String = "ErrorGradleScriptDefinition"
    }

    override fun equals(other: Any?): Boolean = other is ErrorGradleScriptDefinition
    override fun hashCode(): Int = name.hashCode()
}

class ErrorScriptDependenciesResolver(
    private val project: Project,
    private val message: String? = null
) : DependenciesResolver {
    override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
        val importInProgress =
            KotlinDslSyncListener.instance?.tasks?.let { importTasks ->
                synchronized(importTasks) { importTasks.values.any { it.project == project } }
            } ?: false
        val failureMessage = if (importInProgress) {
            KotlinIdeaGradleBundle.message("error.text.highlighting.is.impossible.during.gradle.import")
        } else {
            message ?: KotlinIdeaGradleBundle.message(
                "error.text.failed.to.load.script.definitions.by",
                GradleScriptDefinitionsContributor::class.java.name
            )
        }
        return DependenciesResolver.ResolveResult.Failure(ScriptReport(failureMessage, ScriptReport.Severity.FATAL))
    }
}

fun findStdLibLanguageVersion(classpath: List<Path>): LanguageVersion? = classpath.map { it.parent }.toSet().mapNotNull { path ->
    Files.newDirectoryStream(path) { kotlinStdLibSelector.find(it.name) != null }.use {
        val resultPath = it.firstOrNull() ?: return@use null
        val matchResult = kotlinStdLibSelector.find(resultPath.name) ?: return@use null
        LanguageVersion.fromVersionString(matchResult.groupValues[2])
    }
}.firstOrNull()

private fun loadGradleTemplates(
    projectPath: String,
    templateClass: String,
    gradleHome: String?,
    javaHome: String?,
    templateClasspath: List<Path>,
    additionalClassPath: List<Path>,
    project: Project
): List<ScriptDefinition> {
    val gradleExeSettings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
        project,
        projectPath,
        GradleConstants.SYSTEM_ID
    )
    val defaultCompilerOptions = findStdLibLanguageVersion(templateClasspath)?.let {
        listOf("-language-version", it.versionString)
    } ?: emptyList()

    val hostConfiguration = createHostConfiguration(projectPath, gradleHome, javaHome, gradleExeSettings)
    return loadDefinitionsFromTemplatesByPaths(
        listOf(templateClass),
        templateClasspath,
        hostConfiguration,
        additionalClassPath,
        defaultCompilerOptions
    ).map {
        it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDef ->
            // Expand scope for old gradle script definition
            val version = GradleInstallationManager.getGradleVersion(gradleHome) ?: GradleVersion.current().version
            GradleKotlinScriptDefinitionWrapper(
                it.hostConfiguration,
                legacyDef,
                version,
                defaultCompilerOptions
            )
        } ?: it
    }
}

private fun createHostConfiguration(
    projectPath: String,
    gradleHome: String?,
    javaHome: String?,
    gradleExeSettings: GradleExecutionSettings
): ScriptingHostConfiguration {
    val gradleJvmOptions = gradleExeSettings.jvmArguments

    val environment = mapOf(
        "gradleHome" to gradleHome?.let(::File),
        "gradleJavaHome" to javaHome,

        "projectRoot" to projectPath.let(::File),

        "gradleOptions" to emptyList<String>(), // There is no option in UI to set project wide gradleOptions
        "gradleJvmOptions" to gradleJvmOptions,
        "gradleEnvironmentVariables" to if (gradleExeSettings.isPassParentEnvs) EnvironmentUtil.getEnvironmentMap() else emptyMap()
    )
    return ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        getEnvironment { environment }
    }
}

fun getFullDefinitionsClasspath(gradleHome: String?): Pair<List<Path>, List<Path>> {
    if (gradleHome == null) {
        error(KotlinIdeaGradleBundle.message("error.text.unable.to.get.gradle.home.directory"))
    }

    val gradleLibDir = Path(gradleHome, "lib").let {
        it.takeIf { it.exists() && it.isDirectory() }
            ?: error(KotlinIdeaGradleBundle.message("error.text.invalid.gradle.libraries.directory", it))
    }

    val templateClasspath = Files.newDirectoryStream(gradleLibDir) { kotlinDslDependencySelector.matches(it.name) }
        .use(DirectoryStream<Path>::toList)
        .ifEmpty { error(KotlinIdeaGradleBundle.message("error.text.missing.jars.in.gradle.directory")) }

    scriptingDebugLog { "Gradle definitions classpath: $templateClasspath" }

    val additionalClassPath = kotlinStdlibAndCompiler(gradleLibDir)

    scriptingDebugLog { "Gradle definitions additional classpath: $templateClasspath" }

    return templateClasspath to additionalClassPath
}

private fun kotlinStdlibAndCompiler(gradleLibDir: Path): List<Path> {
    val stdlibPath = gradleLibDir.listDirectoryEntries("kotlin-stdlib-[1-9]*").firstOrNull()
    // additionally need compiler jar to load gradle resolver
    val compilerPath = gradleLibDir.listDirectoryEntries("kotlin-compiler-embeddable*").firstOrNull()
    return listOfNotNull(stdlibPath, compilerPath)
}

private val kotlinDslDependencySelector = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar\$")

fun getDefinitionsTemplateClasspath(gradleHome: String?): List<String> = try {
    getFullDefinitionsClasspath(gradleHome).first.map { it.invariantSeparatorsPathString }
} catch (e: Throwable) {
    scriptingInfoLog("cannot get gradle classpath for Gradle Kotlin DSL scripts: ${e.message}")

    emptyList()
}
