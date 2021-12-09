// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.Decompressor
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.*

const val KOTLINC_DIST_JPS_LIB_XML_NAME = "kotlinc_kotlin_dist.xml"

abstract class KotlinArtifacts(val kotlincDirectory: File) {
    companion object {
        @get:JvmStatic
        val instance: KotlinArtifacts by lazy {
            val homePath = PathManager.getHomePath(false)
            if (homePath != null && File(homePath, ".idea/libraries/$KOTLINC_DIST_JPS_LIB_XML_NAME").exists()) KotlinArtifactsFromSources
            else ProductionKotlinArtifacts
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
    val kotlinStdlibJsSources = File(kotlincLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB_JS_SOURCES)
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

private object ProductionKotlinArtifacts : KotlinArtifacts(run {
    val pluginJar = PathUtil.getResourcePathForClass(ProductionKotlinArtifacts::class.java).toPath()
    if (pluginJar.notExists()) throw IllegalStateException("Plugin JAR not found for class ${ProductionKotlinArtifacts::class.java}")

    val libFile = pluginJar.parent.takeIf { it.name == "lib" }
    if (libFile == null || !libFile.exists()) {
        if ("compile-server" in pluginJar.pathString && pluginJar.resolveSibling("kotlinc").exists()) {
            // WSL JPS build copies all JPS plugin jars to the cache directory, without an intervening 'lib' directory,
            // and the kotlinc directory becomes a subdirectory of the cache directory (see KotlinBuildProcessParametersProvider.getAdditionalPluginPaths())
            pluginJar.parent.resolve("kotlinc").toFile()
        } else {
            // Don't throw exception because someone may want to just try to initialize
            // KotlinArtifacts but won't actually use it. E.g. KotlinPluginMacros does it
            File("\"<invalid_kotlinc_path>\"")
        }
    } else {
        libFile.parent.resolve("kotlinc").toFile()
    }
})

private object KotlinArtifactsFromSources : KotlinArtifacts(run {
    val jar = findMavenLibrary(
        KOTLINC_DIST_JPS_LIB_XML_NAME,
        "org.jetbrains.kotlin",
        "kotlin-dist-for-ide"
    )
    lazyUnpackJar(jar, File(PathManager.getHomePath(), "out").resolve("kotlinc-dist"), "kotlinc")
})

internal fun lazyUnpackJar(jar: File, holderDir: File, dirName: String): File {
    val hashFile = holderDir.resolve("md5")
    val hash = jar.md5()
    val dirWhereToExtract = holderDir.resolve(dirName)
    if (hashFile.exists() && hashFile.readText() == hash) {
        return dirWhereToExtract
    }
    dirWhereToExtract.deleteRecursively()
    dirWhereToExtract.mkdirs()
    hashFile.writeText(hash)
    Decompressor.Zip(jar).extract(dirWhereToExtract)
    return dirWhereToExtract
}

private fun File.md5(): String {
    return MessageDigest.getInstance("MD5").digest(readBytes()).joinToString("") { "%02x".format(it) }
}
