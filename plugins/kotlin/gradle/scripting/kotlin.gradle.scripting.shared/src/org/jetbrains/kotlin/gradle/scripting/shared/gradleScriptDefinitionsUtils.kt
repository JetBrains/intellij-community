// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared

import KotlinGradleScriptingBundle
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleAtLeast
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.scripting.shared.definition.ErrorGradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.LegacyGradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingInfoLog
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

private const val GRADLE_WITH_NEW_SCRIPTING_TEMPLATES = "9.1"

class GradleDefinitionsParams(
    val workingDir: String,
    val gradleHome: String,
    val javaHome: String?, // null due to k1
    val gradleVersion: String?, // null due to k1
    val jvmArguments: List<String>,
    val environment: Map<String, String>,
)

fun loadGradleDefinitions(params: GradleDefinitionsParams): List<GradleScriptDefinition> {
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

    val hostConfiguration = createHostConfiguration(params = params)
    return loadDefinitionsFromTemplates(
        templateClassNames = templateClasses,
        templateClasspath = templateClasspath,
        additionalResolverClasspath = kotlinLibsClassPath,
        baseHostConfiguration = hostConfiguration,
        defaultCompilerOptions = languageVersionCompilerOptions
    ).map {
        it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDefinition ->
            LegacyGradleScriptDefinition(
                legacyDefinition,
                it.hostConfiguration,
                it.evaluationConfiguration,
                it.defaultCompilerOptions,
                params.workingDir
            )
        } ?: GradleScriptDefinition(
            it.compilationConfiguration,
            it.hostConfiguration,
            it.evaluationConfiguration,
            it.defaultCompilerOptions,
            params.workingDir
        )
    }.ifEmpty {
        sequenceOf(ErrorGradleScriptDefinition())
    }.toList()
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

private fun String.toGradleHomePath(): Path = Path(this, "lib").let {
    it.takeIf { it.exists() && it.isDirectory() }
        ?: error(KotlinGradleScriptingBundle.message("error.text.invalid.gradle.libraries.directory", it))
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

fun getDefinitionsTemplateClasspath(gradleHome: String): List<String> = try {
    getFullDefinitionsClasspath(gradleHome.toGradleHomePath()).map { it.invariantSeparatorsPathString }
} catch (e: Throwable) {
    scriptingInfoLog("cannot get classpath for Gradle Kotlin DSL scripts: ${e.message}")

    emptyList()
}

interface GradleScriptCompilationConfigurationKeys

open class GradleScriptCompilationConfigurationBuilder : PropertiesCollection.Builder(),
                                                         GradleScriptCompilationConfigurationKeys {
    companion object : GradleScriptCompilationConfigurationKeys
}

val ScriptCompilationConfigurationKeys.gradle: GradleScriptCompilationConfigurationBuilder
    get() = GradleScriptCompilationConfigurationBuilder()

val GradleScriptCompilationConfigurationKeys.externalProjectPath: PropertiesCollection.Key<String?> by PropertiesCollection.key()


object KotlinGradleScriptEntitySource : EntitySource

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