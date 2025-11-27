// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.definition

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil.isGradleAtLeast
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.script.experimental.api.ScriptCompilationConfigurationKeys
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

    val templateClasses = getGradleTemplatesNames(params.gradleVersion?.let { GradleVersion.version(it) })

    val hostConfiguration = params.toHostConfiguration()
    return loadDefinitionsFromTemplates(
        templateClassNames = templateClasses,
        templateClasspath = templateClasspath,
        additionalResolverClasspath = kotlinLibsClassPath,
        baseHostConfiguration = hostConfiguration,
        defaultCompilerOptions = languageVersionCompilerOptions
    ).map {
        it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDefinition ->
            LegacyGradleScriptDefinition(
                legacyDefinition, it.hostConfiguration, it.evaluationConfiguration, it.defaultCompilerOptions, params.workingDir
            )
        } ?: GradleScriptDefinition(
            it.compilationConfiguration, it.hostConfiguration, it.evaluationConfiguration, it.defaultCompilerOptions, params.workingDir
        )
    }.ifEmpty { sequenceOf(ErrorGradleScriptDefinition()) }.toList()
}

fun getGradleTemplatesNames(gradleVersion: GradleVersion?): List<String> {
    val templateClasses = if (gradleVersion != null && isGradleAtLeast(gradleVersion, GRADLE_WITH_NEW_SCRIPTING_TEMPLATES)) {
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
    return templateClasses
}

private fun findStdLibLanguageVersion(kotlinLibsClassPath: List<Path>): List<String> {
    if (kotlinLibsClassPath.isEmpty()) return emptyList()

    val kotlinStdLibSelector = Regex("^(kotlin-compiler-embeddable|kotlin-stdlib)-(\\d+\\.\\d+).*\\.jar\$")
    val result = kotlinStdLibSelector.find(kotlinLibsClassPath.first().name) ?: return emptyList()

    if (result.groupValues.size < 3) return emptyList()
    val version = result.groupValues[2]
    return LanguageVersion.fromVersionString(version)?.let { listOf("-language-version", it.versionString) } ?: emptyList()
}

private fun GradleDefinitionsParams.toHostConfiguration(): ScriptingHostConfiguration =
    ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        getEnvironment {
            mapOf(
                "gradleHome" to gradleHome.let(::File),
                "gradleJavaHome" to javaHome,
                "projectRoot" to workingDir.let(::File),
                "gradleOptions" to emptyList<String>(), // There is no option in UI to set project wide gradleOptions
                "gradleJvmOptions" to jvmArguments,
                "gradleEnvironmentVariables" to environment
            )
        }
    }

fun String?.toGradleHomePath(): Path {
    if (this == null) error(KotlinGradleScriptingBundle.message("error.text.unable.to.get.gradle.home.directory"))

    return Path(this, "lib").let {
        it.takeIf { it.exists() && it.isDirectory() }
            ?: error(KotlinGradleScriptingBundle.message("error.text.invalid.gradle.libraries.directory", it))
    }
}

fun getFullDefinitionsClasspath(gradleLibDir: Path): List<Path> {
    val templateClasspath = Files.newDirectoryStream(gradleLibDir) { kotlinDslDependencySelector.matches(it.name) }
        .use(DirectoryStream<Path>::toList)
        .ifEmpty { error(KotlinGradleScriptingBundle.message("error.text.missing.jars.in.gradle.directory")) }

    scriptingDebugLog { "Gradle definitions classpath: $templateClasspath" }

    return templateClasspath
}

private fun kotlinStdlibAndCompiler(gradleLibDir: Path): List<Path> {
    val stdlibPath = gradleLibDir.findFirst("kotlin-stdlib-[1-9]*") // additionally need compiler jar to load gradle resolver
    val compilerPath = gradleLibDir.findFirst("kotlin-compiler-embeddable*")
    return listOfNotNull(stdlibPath, compilerPath)
}

private fun Path.findFirst(pattern: String): Path? = Files.newDirectoryStream(this, pattern).firstOrNull()

private val kotlinDslDependencySelector = Regex("^gradle-(?:kotlin-dsl|core|base-services).*\\.jar\$")

interface GradleScriptCompilationConfigurationKeys

open class GradleScriptCompilationConfigurationBuilder : PropertiesCollection.Builder(), GradleScriptCompilationConfigurationKeys {
    companion object : GradleScriptCompilationConfigurationKeys
}

@Suppress("UnusedReceiverParameter")
val ScriptCompilationConfigurationKeys.gradle: GradleScriptCompilationConfigurationBuilder
    get() = GradleScriptCompilationConfigurationBuilder()

val GradleScriptCompilationConfigurationKeys.externalProjectPath: PropertiesCollection.Key<String?> by PropertiesCollection.key()
