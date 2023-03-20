// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.lombok.LombokComponentRegistrar
import org.jetbrains.kotlin.noarg.NoArgComponentRegistrar
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * A helper enum to locate .jar files for bundled compiler plugins. We want to
 * always use the bundled versions of our own compiler plugins to avoid binary incompatibility
 * problems, since Compiler Plugins API in K2 Compiler is not stable yet.
 *
 * This class uses corresponding [CompilerPluginRegistrar] classes only to ensure
 * their availability in compile time; it should not try to instantiate them.
 *
 * [nonBundledJarMarker] is used to detect jars which look like bundled compiler plugins
 * and which should not be enabled to avoid binary incompatability issues.
 *
 * [PathManager.getJarForClass] is used to get the correct location of plugin's jars
 * in any IDE launch scenario (both when run from sources and in dev mode).
 */
@Suppress("unused")
@OptIn(ExperimentalCompilerApi::class)
enum class KotlinK2BundledCompilerPlugins(
    registrarClass: KClass<out CompilerPluginRegistrar>,
    private val nonBundledJarMarker: String,
) {

    ALL_OPEN_COMPILER_PLUGIN(
        AllOpenComponentRegistrar::class,
        "kotlin-allopen-",
    ),

    NO_ARG_COMPILER_PLUGIN(
        NoArgComponentRegistrar::class,
        "kotlin-noarg-",
    ),

    SAM_WITH_RECEIVER_COMPILER_PLUGIN(
        SamWithReceiverComponentRegistrar::class,
        "kotlin-sam-with-receiver-",
    ),

    KOTLINX_SERIALIZATION_COMPILER_PLUGIN(
        SerializationComponentRegistrar::class,
        "kotlin-serialization-",
    ),

    LOMBOK_COMPILER_PLUGIN(
        LombokComponentRegistrar::class,
        "kotlin-lombok-",
    );

    /**
     * TODO: Non-lazy at the moment to ensure all jars are in place.
     */
    val bundledJarLocation: Path =
        PathManager.getJarForClass(registrarClass.java)
            ?: error("Unable to find .jar for '${registrarClass.qualifiedName}' registrar in IDE distribution")

    companion object {
        fun findCorrespondingBundledPlugin(originalJarName: String): KotlinK2BundledCompilerPlugins? =
            KotlinK2BundledCompilerPlugins.values().firstOrNull { it.nonBundledJarMarker in originalJarName }
    }
}
