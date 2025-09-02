// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared

import KotlinGradleScriptingBundle
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleAtLeast
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.kotlin.scripting.definitions.ScriptCompilationConfigurationFromDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.templates.standard.ScriptTemplateWithArgs

private const val GRADLE_WITH_NEW_SCRIPTING_TEMPLATES = "9.1"

class GradleDefinitionsParams(
    val workingDir: String,
    val gradleHome: String?,
    val javaHome: String?, // null due to k1
    val gradleVersion: String?, // null due to k1
    val jvmArguments: List<String>,
    val environment: Map<String, String>,
)

fun loadGradleDefinitions(project: Project, params: GradleDefinitionsParams): List<ScriptDefinition> {
    val loadedDefinitions = try {
        val gradleLibDir = params.gradleHome.toGradleHomePath()

        val templateClasspath = getFullDefinitionsClasspath(gradleLibDir)

        val kotlinLibsClassPath = kotlinStdlibAndCompiler(gradleLibDir)

        val languageVersionCompilerOptions = findStdLibLanguageVersion(kotlinLibsClassPath)

        val templateClasses = if (params.gradleVersion != null && isGradleAtLeast(params.gradleVersion, GRADLE_WITH_NEW_SCRIPTING_TEMPLATES)) {
            listOf(
                "org.gradle.kotlin.dsl.KotlinGradleScriptTemplate",
                "org.gradle.kotlin.dsl.KotlinSettingsScriptTemplate",
                "org.gradle.kotlin.dsl.KotlinProjectScriptTemplate",
            )
        } else {
            listOf(
                "org.gradle.kotlin.dsl.KotlinInitScript",
                "org.gradle.kotlin.dsl.KotlinSettingsScript",
                "org.gradle.kotlin.dsl.KotlinBuildScript",
            )
        }

        loadGradleTemplates(
            params = params,
            templateClasses = templateClasses,
            templateClasspath = templateClasspath,
            additionalClassPath = kotlinLibsClassPath,
            languageVersionCompilerOptions
        ).distinct()
    } catch (t: Throwable) {
        if (t is IllegalStateException) {
            scriptingInfoLog("IllegalStateException loading gradle script templates: ${t.message}")
        } else {
            scriptingDebugLog { "error loading gradle script templates ${t.message}" }
        }
        listOf(ErrorGradleScriptDefinition(project))
    }

    if (loadedDefinitions.isEmpty()) {
        return listOf(ErrorGradleScriptDefinition(project))
    }

    return loadedDefinitions
}

class ErrorGradleScriptDefinition(val project: Project) :
    ScriptDefinition.FromConfigurations(
        ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration),
        ScriptCompilationConfiguration.Default,
        ScriptEvaluationConfiguration {
            hostConfiguration(ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration))
        }
    ) {

    init {
        order = Int.MIN_VALUE
    }

    override val name: String get() = KotlinGradleScriptingBundle.message("text.default.kotlin.gradle.script")
    override val fileExtension: String = "gradle.kts"
    override val baseClassType: KotlinType = KotlinType(ScriptTemplateWithArgs::class)

    override fun toString(): String = "ErrorGradleScriptDefinition"

    override fun equals(other: Any?): Boolean = other is ErrorGradleScriptDefinition
    override fun hashCode(): Int = name.hashCode()
}

private fun findStdLibLanguageVersion(kotlinLibsClassPath: List<Path>): List<String> {
    if (kotlinLibsClassPath.isEmpty()) return emptyList()

    val kotlinStdLibSelector = Regex("^(kotlin-compiler-embeddable|kotlin-stdlib)-(\\d+\\.\\d+).*\\.jar\$")
    val result = kotlinStdLibSelector.find(kotlinLibsClassPath.first().name) ?: return emptyList()

    if (result.groupValues.size < 3) return emptyList()
    val version = result.groupValues[2]
    return LanguageVersion.fromVersionString(version)?.let { listOf("-language-version", it.versionString) }
        ?: emptyList()
}

private fun loadGradleTemplates(
    params: GradleDefinitionsParams,
    templateClasses: List<String>,
    templateClasspath: List<Path>,
    additionalClassPath: List<Path>,
    defaultCompilerOptions: List<String>
): List<ScriptDefinition> {
    val hostConfiguration = createHostConfiguration(params)

    return loadDefinitionsFromTemplatesByPaths(
        templateClasses,
        templateClasspath,
        hostConfiguration,
        additionalClassPath,
        defaultCompilerOptions
    ).map {
        it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDefinition ->
            LegacyGradleScriptDefinitionWrapper(
                legacyDefinition,
                it.hostConfiguration,
                it.evaluationConfiguration,
                it.defaultCompilerOptions,
                params.workingDir
            )
        } ?: GradleScriptDefinitionWrapper(
            it.compilationConfiguration,
            it.hostConfiguration,
            it.evaluationConfiguration,
            it.defaultCompilerOptions,
            params.workingDir
        )
    }
}

private fun createHostConfiguration(params: GradleDefinitionsParams): ScriptingHostConfiguration =
    ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        getEnvironment {
            mapOf(
                "gradleHome" to params.gradleHome?.let(::File),
                "gradleJavaHome" to params.javaHome,
                "projectRoot" to params.workingDir.let(::File),
                "gradleOptions" to emptyList<String>(), // There is no option in UI to set project wide gradleOptions
                "gradleJvmOptions" to params.jvmArguments,
                "gradleEnvironmentVariables" to params.environment
            )
        }
    }

private fun String?.toGradleHomePath(): Path {
    if (this == null) error(KotlinGradleScriptingBundle.message("error.text.unable.to.get.gradle.home.directory"))

    return Path(this, "lib").let {
        it.takeIf { it.exists() && it.isDirectory() }
            ?: error(KotlinGradleScriptingBundle.message("error.text.invalid.gradle.libraries.directory", it))
    }
}

private fun getFullDefinitionsClasspath(gradleLibDir: Path): List<Path> {
    val templateClasspath = Files.newDirectoryStream(gradleLibDir) { kotlinDslDependencySelector.matches(it.name) }
        .use(DirectoryStream<Path>::toList)
        .ifEmpty { error(KotlinGradleScriptingBundle.message("error.text.missing.jars.in.gradle.directory")) }

    scriptingDebugLog { "Gradle definitions classpath: $templateClasspath" }

    return templateClasspath
}

private fun kotlinStdlibAndCompiler(gradleLibDir: Path): List<Path> {
    val stdlibPath = gradleLibDir.findFirst("kotlin-stdlib-[1-9]*")
    // additionally need compiler jar to load gradle resolver
    val compilerPath = gradleLibDir.findFirst("kotlin-compiler-embeddable*")
    return listOfNotNull(stdlibPath, compilerPath)
}

private fun Path.findFirst(pattern: String): Path? = Files.newDirectoryStream(this, pattern).firstOrNull()

private val kotlinDslDependencySelector = Regex("^gradle-(?:kotlin-dsl|core|base-services).*\\.jar\$")

fun getDefinitionsTemplateClasspath(gradleHome: String?): List<String> = try {
    getFullDefinitionsClasspath(gradleHome.toGradleHomePath()).map { it.invariantSeparatorsPathString }
} catch (e: Throwable) {
    scriptingInfoLog("cannot get gradle classpath for Gradle Kotlin DSL scripts: ${e.message}")

    emptyList()
}

const val DEFINITION_ID: String = "ideGradleScriptDefinitionId"

class LegacyGradleScriptDefinitionWrapper(
    private val _legacyDefinition: KotlinScriptDefinitionFromAnnotatedTemplate,
    hostConfiguration: ScriptingHostConfiguration,
    evaluationConfiguration: ScriptEvaluationConfiguration?,
    defaultCompilerOptions: Iterable<String>,
    externalProjectPath: String? = null
) : GradleScriptDefinitionWrapper(
    ScriptCompilationConfigurationFromDefinition(
        hostConfiguration,
        _legacyDefinition
    ),
    hostConfiguration,
    evaluationConfiguration,
    defaultCompilerOptions,
    externalProjectPath
) {
    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = super.compilationConfiguration.with {
            @Suppress("DEPRECATION_ERROR")
            fileNamePattern.put(_legacyDefinition.scriptFilePattern.pattern)
        }
}

open class GradleScriptDefinitionWrapper(
    compilationConfiguration: ScriptCompilationConfiguration,
    override val hostConfiguration: ScriptingHostConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?,
    override val defaultCompilerOptions: Iterable<String>,
    private val _externalProjectPath: String?,
) : ScriptDefinition.FromConfigurationsBase() {

    init {
        order = Int.MIN_VALUE
    }

    override val definitionId: String
        get() = DEFINITION_ID

    override val canDefinitionBeSwitchedOff: Boolean = false

    override val compilationConfiguration: ScriptCompilationConfiguration by lazy {
        compilationConfiguration.with {
            gradle {
                externalProjectPath(_externalProjectPath)
            }
            ide {
                acceptedLocations.put(listOf(ScriptAcceptedLocation.Project))
            }
        }
    }

    fun with(body: ScriptCompilationConfiguration.Builder.() -> Unit): GradleScriptDefinitionWrapper {
        val newConfiguration = ScriptCompilationConfiguration(compilationConfiguration, body = body)
        return GradleScriptDefinitionWrapper(
            newConfiguration,
            hostConfiguration,
            evaluationConfiguration,
            defaultCompilerOptions,
            _externalProjectPath
        )
    }
}

interface GradleScriptCompilationConfigurationKeys

open class GradleScriptCompilationConfigurationBuilder : PropertiesCollection.Builder(),
                                                         GradleScriptCompilationConfigurationKeys {
    companion object : GradleScriptCompilationConfigurationKeys
}

val ScriptCompilationConfigurationKeys.gradle: GradleScriptCompilationConfigurationBuilder
    get() = GradleScriptCompilationConfigurationBuilder()

val GradleScriptCompilationConfigurationKeys.externalProjectPath: PropertiesCollection.Key<String?> by PropertiesCollection.key()


data class KotlinGradleScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)

class GradleScriptModelData(
    val models: Collection<GradleScriptModel>,
    val javaHome: String? = null,
)

class GradleScriptModel(
    val virtualFile: VirtualFile,
    val classPath: List<String>,
    val sourcePath: List<String>,
    val imports: List<String>,
)