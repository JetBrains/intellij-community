// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File

@ApiStatus.Internal
object KotlinArtifactConstants {
    @NlsSafe
    const val KOTLIN_MAVEN_GROUP_ID = "org.jetbrains.kotlin"

    @Deprecated(
        "Deprecated because new \"meta pom\" format (KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID) should be used. " +
                "This constant should be used only for being possible to compile intellij repo"
    )
    @NlsSafe
    const val OLD_KOTLIN_DIST_ARTIFACT_ID = "kotlin-dist-for-ide"

    @NlsSafe
    const val KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID = "kotlin-dist-for-jps-meta"

    @Deprecated(
        "Deprecated because new KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID should be used. " +
                "This constant should be used only for being possible to compile intellij repo"
    )
    @NlsSafe
    const val OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID = "kotlin-jps-plugin-classpath"

    @NlsSafe
    const val KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID = "kotlin-jps-plugin"

    val KOTLIN_DIST_LOCATION_PREFIX: File = File(PathManager.getSystemPath(), "kotlin-dist-for-ide")
}

/**
 * Layout of bundled Kotlin dist artifacts. Ideally, this class shouldn't exist at all, because bundled dist should be used only for
 * compilation via JPS => no need to know about dist jars layout. Try to avoid using this class.
 *
 * For tests use [TestKotlinArtifacts]
 */
object KotlinArtifacts {
    private val kotlincBinDirectory: File = File(KotlinPluginLayout.kotlinc, "bin")

    private val kotlincLibDirectory: File = File(KotlinPluginLayout.kotlinc, "lib")

    @JvmStatic
    val jetbrainsAnnotations: File = File(kotlincLibDirectory, KotlinArtifactNames.JETBRAINS_ANNOTATIONS)

    @JvmStatic
    val kotlinStdlib: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB)

    @JvmStatic
    val kotlinStdlibSources: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_SOURCES)

    @JvmStatic
    val kotlinStdlibJdk7: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK7)

    @JvmStatic
    val kotlinStdlibJdk7Sources: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK7_SOURCES)

    @JvmStatic
    val kotlinStdlibJdk8: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK8)

    @JvmStatic
    val kotlinStdlibJdk8Sources: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JDK8_SOURCES)

    @JvmStatic
    val kotlinReflect: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_REFLECT)

    @JvmStatic
    val kotlinStdlibJs: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JS)

    @JvmStatic
    val kotlinMainKts: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_MAIN_KTS)

    @JvmStatic
    val kotlinScriptRuntime: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME)

    @JvmStatic
    val kotlinScriptingCommon: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON)

    @JvmStatic
    val kotlinScriptingJvm: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_JVM)

    @JvmStatic
    val kotlinCompiler: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_COMPILER)

    @JvmStatic
    val lombokCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.LOMBOK_COMPILER_PLUGIN)

    @JvmStatic
    val trove4j: File = File(kotlincLibDirectory, KotlinArtifactNames.TROVE4J)

    @JvmStatic
    val kotlinDaemon: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_DAEMON)

    @JvmStatic
    val kotlinScriptingCompiler: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER)

    @JvmStatic
    val kotlinScriptingCompilerImpl: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL)

    @JvmStatic
    val allopenCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.ALLOPEN_COMPILER_PLUGIN)

    @JvmStatic
    val noargCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.NOARG_COMPILER_PLUGIN)

    @JvmStatic
    val samWithReceiverCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.SAM_WITH_RECEIVER_COMPILER_PLUGIN)

    @JvmStatic
    val assignmentCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.ASSIGNMENT_COMPILER_PLUGIN)

    @JvmStatic
    val kotlinxSerializationCompilerPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)

    @JvmStatic
    val powerAssertPlugin: File = File(kotlincLibDirectory, KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN)

    @JvmStatic
    val kotlinPreloader: File = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_PRELOADER)

    @JvmStatic
    val kotlinc: File = File(kotlincBinDirectory, KotlinArtifactNames.KOTLINC)
}
