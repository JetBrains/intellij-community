// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared

import KotlinGradleScriptingBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.EnvironmentUtil
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.NewScriptFileInfo
import org.jetbrains.kotlin.idea.core.script.k2.configurationResolverDelegate
import org.jetbrains.kotlin.idea.core.script.k2.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.idea.core.script.k2.scriptWorkspaceModelManagerDelegate
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.kotlin.scripting.definitions.ScriptCompilationConfigurationFromDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
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

fun loadGradleDefinitions(
    workingDir: String,
    gradleHome: String?,
    javaHome: String?,
    project: Project
): List<ScriptDefinition> {
    val loadedDefinitions = try {
        val gradleLibDir = gradleHome.toGradleHomePath()

        val templateClasspath = getFullDefinitionsClasspath(gradleLibDir)
        scriptingDebugLog { "Gradle definitions additional classpath: $templateClasspath" }

        val kotlinLibsClassPath = kotlinStdlibAndCompiler(gradleLibDir)

        val languageVersionCompilerOptions = findStdLibLanguageVersion(kotlinLibsClassPath)

        val templateClasses = listOf(
            "org.gradle.kotlin.dsl.KotlinInitScript",
            "org.gradle.kotlin.dsl.KotlinSettingsScript",
            "org.gradle.kotlin.dsl.KotlinBuildScript",
        )

        loadGradleTemplates(
            workingDir,
            templateClasses = templateClasses,
            gradleHome = gradleHome,
            javaHome = javaHome,
            templateClasspath = templateClasspath,
            additionalClassPath = kotlinLibsClassPath,
            project,
            languageVersionCompilerOptions
        ).distinct()
    } catch (t: Throwable) {
        // TODO: review exception handling

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
    projectPath: String,
    templateClasses: List<String>,
    gradleHome: String?,
    javaHome: String?,
    templateClasspath: List<Path>,
    additionalClassPath: List<Path>,
    project: Project,
    defaultCompilerOptions: List<String>
): List<ScriptDefinition> {
    val gradleExeSettings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
        project,
        projectPath,
        GradleConstants.SYSTEM_ID
    )

    val hostConfiguration = createHostConfiguration(projectPath, gradleHome, javaHome, gradleExeSettings)

    return loadDefinitionsFromTemplatesByPaths(
        templateClasses,
        templateClasspath,
        hostConfiguration,
        additionalClassPath,
        defaultCompilerOptions
    ).map {
        it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDefinition ->
            GradleKotlinScriptDefinitionWrapper(
                legacyDefinition,
                it.hostConfiguration,
                it.evaluationConfiguration,
                it.defaultCompilerOptions,
                project,
                projectPath
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

private val kotlinDslDependencySelector = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar\$")

fun getDefinitionsTemplateClasspath(gradleHome: String?): List<String> = try {
    getFullDefinitionsClasspath(gradleHome.toGradleHomePath()).map { it.invariantSeparatorsPathString }
} catch (e: Throwable) {
    scriptingInfoLog("cannot get gradle classpath for Gradle Kotlin DSL scripts: ${e.message}")

    emptyList()
}

const val DEFINITION_ID: String = "ideGradleScriptDefinitionId"

class GradleKotlinScriptDefinitionWrapper(
    legacyDefinition: KotlinScriptDefinitionFromAnnotatedTemplate,
    override val hostConfiguration: ScriptingHostConfiguration,
    override val evaluationConfiguration: ScriptEvaluationConfiguration?,
    override val defaultCompilerOptions: Iterable<String>,
    val project: Project,
    externalProjectPath: String? = null
) : ScriptDefinition.FromConfigurationsBase() {

    init {
        order = Int.MIN_VALUE
    }

    override val definitionId: String
        get() = DEFINITION_ID

    override val compilationConfiguration: ScriptCompilationConfiguration by lazy {
        ScriptCompilationConfigurationFromDefinition(
            hostConfiguration,
            legacyDefinition
        ).with {
            @Suppress("DEPRECATION_ERROR")
            fileNamePattern.put(legacyDefinition.scriptFilePattern.pattern)
            gradle {
                externalProjectPath(externalProjectPath)
            }
            ide {
                acceptedLocations.put(listOf(ScriptAcceptedLocation.Project))
                kotlinScriptTemplateInfo(NewScriptFileInfo().apply {
                    id = "gradle-kts"
                    title = ".gradle.kts"
                    templateName = "Kotlin Script Gradle"
                })
                configurationResolverDelegate {
                    GradleScriptRefinedConfigurationProvider.getInstance(project)
                }
                scriptWorkspaceModelManagerDelegate {
                    GradleScriptRefinedConfigurationProvider.getInstance(project)
                }
            }
        }
    }

    override val canDefinitionBeSwitchedOff: Boolean = false
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

class GradleScriptModel(
    val virtualFile: VirtualFile,
    val classPath: List<String>,
    val sourcePath: List<String>,
    val imports: List<String>,
    val javaHome: String? = null,
)