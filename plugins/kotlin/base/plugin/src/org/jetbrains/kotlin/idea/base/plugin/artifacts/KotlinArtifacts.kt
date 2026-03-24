// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames.JETBRAINS_ANNOTATIONS
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
object KotlinArtifactConstants {
    @NlsSafe
    const val KOTLIN_MAVEN_GROUP_ID: String = "org.jetbrains.kotlin"

    @Deprecated(
        "Deprecated because new \"meta pom\" format (KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID) should be used. " +
                "This constant should be used only for being possible to compile intellij repo"
    )
    @NlsSafe
    const val OLD_KOTLIN_DIST_ARTIFACT_ID: String = "kotlin-dist-for-ide"

    @NlsSafe
    const val KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID: String = "kotlin-dist-for-jps-meta"

    @Deprecated(
        "Deprecated because new KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID should be used. " +
                "This constant should be used only for being possible to compile intellij repo"
    )
    @NlsSafe
    const val OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID: String = "kotlin-jps-plugin-classpath"

    @NlsSafe
    const val KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID: String = "kotlin-jps-plugin"

    val KOTLIN_DIST_LOCATION_PREFIX_PATH: Path = PathManager.getSystemDir().resolve("kotlin-dist-for-ide")

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use KOTLIN_DIST_LOCATION_PREFIX_PATH instead", ReplaceWith("KOTLIN_DIST_LOCATION_PREFIX_PATH"))
    val KOTLIN_DIST_LOCATION_PREFIX: File
        get() = KOTLIN_DIST_LOCATION_PREFIX_PATH.toFile()
}

/**
 * Layout of bundled Kotlin dist artifacts. Ideally, this class shouldn't exist at all, because bundled dist should be used only for
 * compilation via JPS => no need to know about dist jars layout. Try to avoid using this class.
 *
 * For tests use [TestKotlinArtifacts]
 */
object KotlinArtifacts {
    private val kotlincBinDirectoryPath: Path = KotlinPluginLayout.kotlincPath.resolve("bin")

    private val kotlincLibDirectoryPath: Path = KotlinPluginLayout.kotlincPath.resolve("lib")

    @JvmStatic
    val jetbrainsAnnotationsPath: Path = kotlincLibDirectoryPath.resolve(JETBRAINS_ANNOTATIONS)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use jetbrainsAnnotationsPath instead", ReplaceWith("jetbrainsAnnotationsPath"))
    @JvmStatic
    val jetbrainsAnnotations: File
        get() = jetbrainsAnnotationsPath.toFile()

    @JvmStatic
    val kotlinStdlibPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibPath instead", ReplaceWith("kotlinStdlibPath"))
    @JvmStatic
    val kotlinStdlib: File
        get() = kotlinStdlibPath.toFile()

    @JvmStatic
    val kotlinStdlibSourcesPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_SOURCES)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibSourcesPath instead", ReplaceWith("kotlinStdlibSourcesPath"))
    @JvmStatic
    val kotlinStdlibSources: File
        get() = kotlinStdlibSourcesPath.toFile()

    @JvmStatic
    val kotlinStdlibJdk7Path: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_JDK7)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibJdk7Path instead", ReplaceWith("kotlinStdlibJdk7Path"))
    @JvmStatic
    val kotlinStdlibJdk7: File
        get() = kotlinStdlibJdk7Path.toFile()

    @JvmStatic
    val kotlinStdlibJdk7SourcesPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_JDK7_SOURCES)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibJdk7SourcesPath instead", ReplaceWith("kotlinStdlibJdk7SourcesPath"))
    @JvmStatic
    val kotlinStdlibJdk7Sources: File
        get() = kotlinStdlibJdk7SourcesPath.toFile()

    @JvmStatic
    val kotlinStdlibJdk8Path: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_JDK8)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibJdk8Path instead", ReplaceWith("kotlinStdlibJdk8Path"))
    @JvmStatic
    val kotlinStdlibJdk8: File
        get() = kotlinStdlibJdk8Path.toFile()

    @JvmStatic
    val kotlinStdlibJdk8SourcesPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_JDK8_SOURCES)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibJdk8SourcesPath instead", ReplaceWith("kotlinStdlibJdk8SourcesPath"))
    @JvmStatic
    val kotlinStdlibJdk8Sources: File
        get() = kotlinStdlibJdk8SourcesPath.toFile()

    @JvmStatic
    val kotlinReflectPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_REFLECT)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinReflectPath instead", ReplaceWith("kotlinReflectPath"))
    @JvmStatic
    val kotlinReflect: File
        get() = kotlinReflectPath.toFile()

    @JvmStatic
    val kotlinStdlibJsPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_STDLIB_JS)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinStdlibJsPath instead", ReplaceWith("kotlinStdlibJsPath"))
    @JvmStatic
    val kotlinStdlibJs: File
        get() = kotlinStdlibJsPath.toFile()

    @JvmStatic
    val kotlinMainKtsPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_MAIN_KTS)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinMainKtsPath instead", ReplaceWith("kotlinMainKtsPath"))
    @JvmStatic
    val kotlinMainKts: File
        get() = kotlinMainKtsPath.toFile()

    @JvmStatic
    val kotlinScriptRuntimePath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinScriptRuntimePath instead", ReplaceWith("kotlinScriptRuntimePath"))
    @JvmStatic
    val kotlinScriptRuntime: File
        get() = kotlinScriptRuntimePath.toFile()

    @JvmStatic
    val kotlinScriptingCommonPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinScriptingCommonPath instead", ReplaceWith("kotlinScriptingCommonPath"))
    @JvmStatic
    val kotlinScriptingCommon: File
        get() = kotlinScriptingCommonPath.toFile()

    @JvmStatic
    val kotlinScriptingJvmPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_SCRIPTING_JVM)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinScriptingJvmPath instead", ReplaceWith("kotlinScriptingJvmPath"))
    @JvmStatic
    val kotlinScriptingJvm: File
        get() = kotlinScriptingJvmPath.toFile()

    @JvmStatic
    val kotlinCompilerPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_COMPILER)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinCompilerPath instead", ReplaceWith("kotlinCompilerPath"))
    @JvmStatic
    val kotlinCompiler: File
        get() = kotlinCompilerPath.toFile()

    @JvmStatic
    val lombokCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.LOMBOK_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use lombokCompilerPluginPath instead", ReplaceWith("lombokCompilerPluginPath"))
    @JvmStatic
    val lombokCompilerPlugin: File
        get() = lombokCompilerPluginPath.toFile()

    @JvmStatic
    val trove4jPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.TROVE4J)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use trove4jPath instead", ReplaceWith("trove4jPath"))
    @JvmStatic
    val trove4j: File
        get() = trove4jPath.toFile()

    @JvmStatic
    val kotlinDaemonPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_DAEMON)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinDaemonPath instead", ReplaceWith("kotlinDaemonPath"))
    @JvmStatic
    val kotlinDaemon: File
        get() = kotlinDaemonPath.toFile()

    @JvmStatic
    val kotlinScriptingCompilerPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinScriptingCompilerPath instead", ReplaceWith("kotlinScriptingCompilerPath"))
    @JvmStatic
    val kotlinScriptingCompiler: File
        get() = kotlinScriptingCompilerPath.toFile()

    @JvmStatic
    val kotlinScriptingCompilerImplPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinScriptingCompilerImplPath instead", ReplaceWith("kotlinScriptingCompilerImplPath"))
    @JvmStatic
    val kotlinScriptingCompilerImpl: File
        get() = kotlinScriptingCompilerImplPath.toFile()

    @JvmStatic
    val allopenCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.ALLOPEN_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use allopenCompilerPluginPath instead", ReplaceWith("allopenCompilerPluginPath"))
    @JvmStatic
    val allopenCompilerPlugin: File 
        get() = allopenCompilerPluginPath.toFile()

    @JvmStatic
    val noargCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.NOARG_COMPILER_PLUGIN)

    
    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use noargCompilerPluginPath instead", ReplaceWith("noargCompilerPluginPath"))
    @JvmStatic
    val noargCompilerPlugin: File 
        get() = noargCompilerPluginPath.toFile()

    @JvmStatic
    val samWithReceiverCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.SAM_WITH_RECEIVER_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use samWithReceiverCompilerPluginPath instead", ReplaceWith("samWithReceiverCompilerPluginPath"))
    @JvmStatic
    val samWithReceiverCompilerPlugin: File 
        get() = samWithReceiverCompilerPluginPath.toFile()

    @JvmStatic
    val assignmentCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.ASSIGNMENT_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use assignmentCompilerPluginPath instead", ReplaceWith("assignmentCompilerPluginPath"))
    @JvmStatic
    val assignmentCompilerPlugin: File 
        get() = assignmentCompilerPluginPath.toFile()

    @JvmStatic
    val kotlinxSerializationCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinxSerializationCompilerPluginPath instead", ReplaceWith("kotlinxSerializationCompilerPluginPath"))
    @JvmStatic
    val kotlinxSerializationCompilerPlugin: File 
        get() = kotlinxSerializationCompilerPluginPath.toFile()

    @JvmStatic
    val powerAssertPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use powerAssertPluginPath instead", ReplaceWith("powerAssertPluginPath"))
    @JvmStatic
    val powerAssertPlugin: File
        get() = powerAssertPluginPath.toFile()

    @JvmStatic
    val kotlinDataFrameCompilerPluginPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_DATAFRAME_COMPILER_PLUGIN)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinDataFrameCompilerPluginPath instead", ReplaceWith("kotlinDataFrameCompilerPluginPath"))
    @JvmStatic
    val kotlinDataFrameCompilerPlugin: File
        get() = kotlinDataFrameCompilerPluginPath.toFile()

    @JvmStatic
    val kotlinPreloaderPath: Path = kotlincLibDirectoryPath.resolve(KotlinArtifactNames.KOTLIN_PRELOADER)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlinPreloaderPath instead", ReplaceWith("kotlinPreloaderPath"))
    @JvmStatic
    val kotlinPreloader: File
        get() = kotlinPreloaderPath.toFile()

    @JvmStatic
    val kotlincPath: Path = kotlincBinDirectoryPath.resolve(KotlinArtifactNames.KOTLINC)

    @Suppress("IO_FILE_USAGE")
    @Deprecated("Use kotlincPath instead", ReplaceWith("kotlincPath"))
    @JvmStatic
    val kotlinc: File
        get() = kotlincPath.toFile()
}
