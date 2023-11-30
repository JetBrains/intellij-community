// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar
import org.jetbrains.kotlin.assignment.plugin.AssignmentComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.lombok.LombokComponentRegistrar
import org.jetbrains.kotlin.noarg.NoArgComponentRegistrar
import org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.FirScriptingCompilerExtensionRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingK2CompilerPluginRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.reflect.KClass

/**
 * A helper enum to locate .jar files for bundled compiler plugins. We want to
 * always use the bundled versions of our own compiler plugins to avoid binary incompatibility
 * problems, since Compiler Plugins API in K2 Compiler is not stable yet.
 *
 * This enum uses corresponding [CompilerPluginRegistrar] classes only to ensure
 * their availability in compile time; it should not try to instantiate them.
 *
 * Jars with specified compiler plugins are identified by the content of
 * one of [COMPILER_PLUGIN_REGISTRAR_FILES] inside of them. If any of those files contains one of
 * [registrarClassName]s, then we consider this jar to be a compiler plugin.
 *
 * [PathManager.getJarForClass] is used to get the correct location of plugin's jars
 * in any IDE launch scenario (both when run from sources and in dev mode).
 */
@Suppress("unused")
@OptIn(ExperimentalCompilerApi::class)
enum class KotlinK2BundledCompilerPlugins(
    registrarClass: KClass<out CompilerPluginRegistrar>,
) {

    ALL_OPEN_COMPILER_PLUGIN(
        AllOpenComponentRegistrar::class,
    ),

    NO_ARG_COMPILER_PLUGIN(
        NoArgComponentRegistrar::class,
    ),

    SAM_WITH_RECEIVER_COMPILER_PLUGIN(
        SamWithReceiverComponentRegistrar::class,
    ),

    ASSIGNMENT_COMPILER_PLUGIN(
        AssignmentComponentRegistrar::class,
    ),

    KOTLINX_SERIALIZATION_COMPILER_PLUGIN(
        SerializationComponentRegistrar::class,
    ),

    LOMBOK_COMPILER_PLUGIN(
        LombokComponentRegistrar::class,
    ),

    PARCELIZE_COMPILER_PLUGIN(
        ParcelizeComponentRegistrar::class
    ),

    SCRIPTING_COMPILER_PLUGIN(
        ScriptingK2CompilerPluginRegistrar::class,
    );

    private val registrarClassName: String =
        registrarClass.qualifiedName ?: error("${registrarClass} does not have a qualified name")

    /**
     * TODO: Non-lazy at the moment to ensure all jars are in place.
     */
    val bundledJarLocation: Path =
        PathManager.getJarForClass(registrarClass.java)
            ?: error("Unable to find .jar for '$registrarClassName' registrar in IDE distribution")

    companion object {
        private val COMPILER_PLUGIN_REGISTRAR_FILES: Set<String> = setOf(
            "META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar",   // default registrar location
            "META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar",        // old registrar location, see KT-52665
        )

        fun findCorrespondingBundledPlugin(originalJar: Path): KotlinK2BundledCompilerPlugins? {
            val compilerPluginRegistrarContent =
                COMPILER_PLUGIN_REGISTRAR_FILES.firstNotNullOfOrNull { readFileContentFromJar(originalJar, it) } ?: return null

            return KotlinK2BundledCompilerPlugins.entries.firstOrNull { it.registrarClassName in compilerPluginRegistrarContent }
        }
    }
}

private fun readFileContentFromJar(jarFile: Path, pathInJar: String): String? {
    if (jarFile.notExists() || jarFile.extension != "jar") return null

    FileSystems.newFileSystem(jarFile).use { fileSystem ->
        val registrarPath = fileSystem.getPath(pathInJar)
        if (registrarPath.notExists()) return null

        return registrarPath.readText()
    }
}
