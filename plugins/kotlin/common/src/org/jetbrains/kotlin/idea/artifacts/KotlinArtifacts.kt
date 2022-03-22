// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.artifacts

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.application.PathManager
import com.intellij.util.io.Decompressor
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File

class KotlinArtifacts private constructor(val kotlincDirectory: File) {
    companion object {
        const val KOTLIN_MAVEN_GROUP_ID = "org.jetbrains.kotlin"
        const val KOTLIN_DIST_ARTIFACT_ID = "kotlin-dist-for-ide"
        const val KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID = "kotlin-jps-plugin-classpath"
        val KOTLIN_DIST_LOCATION_PREFIX = File(PathManager.getSystemPath(), KOTLIN_DIST_ARTIFACT_ID)

        @get:JvmStatic
        val instance: KotlinArtifacts by lazy {
            KotlinArtifacts(KotlinPluginLayout.instance.kotlinc)
        }
    }

    val kotlincLibDirectory = File(kotlincDirectory, "lib")

    val jetbrainsAnnotations = File(kotlincLibDirectory, KotlinArtifactNames.JETBRAINS_ANNOTATIONS)
    val kotlinStdlib = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB)
    val kotlinStdlibSources = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_SOURCES)
    val kotlinStdlibJdk7 = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK7)
    val kotlinStdlibJdk7Sources = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK7_SOURCES)
    val kotlinStdlibJdk8 = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK8)
    val kotlinStdlibJdk8Sources = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK8_SOURCES)
    val kotlinReflect = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_REFLECT)
    val kotlinStdlibJs = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JS)
    val kotlinTest = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_TEST)
    val kotlinTestJunit = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_TEST_JUNIT)
    val kotlinTestJs = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_TEST_JS)
    val kotlinMainKts = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_MAIN_KTS)
    val kotlinScriptRuntime = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME)
    val kotlinScriptingCommon = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON)
    val kotlinScriptingJvm = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_JVM)
    val kotlinCompiler: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_COMPILER)
    val lombokCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.LOMBOK_COMPILER_PLUGIN)
    val kotlinAnnotationsJvm: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_ANNOTATIONS_JVM)
    val trove4j = File(kotlincLibDirectory, KotlinArtifactNames.TROVE4J)
    val kotlinDaemon = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_DAEMON)
    val kotlinScriptingCompiler = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER)
    val kotlinScriptingCompilerImpl = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL)
    val allopenCompilerPlugin = File(kotlincLibDirectory, KotlinArtifactNames.ALLOPEN_COMPILER_PLUGIN)
    val noargCompilerPlugin = File(kotlincLibDirectory, KotlinArtifactNames.NOARG_COMPILER_PLUGIN)
    val samWithReceiverCompilerPlugin = File(kotlincLibDirectory, KotlinArtifactNames.SAM_WITH_RECEIVER_COMPILER_PLUGIN)
    val kotlinxSerializationCompilerPlugin = File(kotlincLibDirectory, KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)
}

fun lazyUnpackJar(jar: File, destination: File): File {
    val unpackedDistTimestamp = destination.lastModified()
    val packedDistTimestamp = jar.lastModified()
    if (unpackedDistTimestamp != 0L && packedDistTimestamp != 0L && unpackedDistTimestamp >= packedDistTimestamp) {
        return destination
    }
    destination.deleteRecursively()

    Decompressor.Zip(jar).extract(destination)
    check(destination.isDirectory)
    return destination
}

fun resolveMavenArtifactInMavenRepo(mavenRepo: File, groupId: String, artifactId: String, version: String) =
    mavenRepo.resolve(groupId.replace(".", "/"))
        .resolve(artifactId)
        .resolve(version)
        .resolve("$artifactId-$version.jar")

fun getMavenArtifactJarPath(groupId: String, artifactId: String, version: String) =
    resolveMavenArtifactInMavenRepo(JarRepositoryManager.getLocalRepositoryPath(), groupId, artifactId, version)
