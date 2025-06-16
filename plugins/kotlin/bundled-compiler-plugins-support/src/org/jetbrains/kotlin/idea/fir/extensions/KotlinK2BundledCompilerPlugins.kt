// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.allopen.AllOpenComponentRegistrar
import org.jetbrains.kotlin.assignment.plugin.AssignmentComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.lombok.LombokComponentRegistrar
import org.jetbrains.kotlin.noarg.NoArgComponentRegistrar
import org.jetbrains.kotlin.parcelize.ParcelizeComponentRegistrar
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingK2CompilerPluginRegistrar
import org.jetbrains.kotlinx.jspo.compiler.cli.JsPlainObjectsComponentRegistrar
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.jetbrains.kotlinx.dataframe.plugin.FirDataFrameComponentRegistrar
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * A helper enum to locate .jar files for bundled compiler plugins. We want to
 * always use the bundled versions of our own compiler plugins to avoid binary incompatibility
 * problems, since Compiler Plugins API in K2 Compiler is not stable yet.
 *
 * This enum uses corresponding [CompilerPluginRegistrar] classes only to ensure
 * their availability in compile time; it should not try to instantiate them.
 *
 * [PathManager.getJarForClass] is used to get the correct location of plugin's jars
 * in any IDE launch scenario (both when run from sources and in dev mode).
 *
 * @see CompilerPluginRegistrarUtils
 * @see KotlinBundledFirCompilerPluginProvider
 */
@OptIn(ExperimentalCompilerApi::class)
enum class KotlinK2BundledCompilerPlugins(
    registrarClass: KClass<out CompilerPluginRegistrar>,
) {

    ALL_OPEN_COMPILER_PLUGIN(
        AllOpenComponentRegistrar::class,
    ),

    COMPOSE_COMPILER_PLUGIN(
        ComposePluginRegistrar::class
    ),

    JS_PLAIN_OBJECTS_COMPILER_PLUGIN(
        JsPlainObjectsComponentRegistrar::class
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
    ),

    DATAFRAME_COMPILER_PLUGIN(
        FirDataFrameComponentRegistrar::class
    );

    internal val registrarClassName: String =
        registrarClass.qualifiedName ?: error("${registrarClass} does not have a qualified name")

    /**
     * TODO: Non-lazy at the moment to ensure all jars are in place.
     */
    val bundledJarLocation: Path =
        PathManager.getJarForClass(registrarClass.java)
            ?: error("Unable to find .jar for '$registrarClassName' registrar in IDE distribution")

    @Deprecated("This companion object is left for binary compatibility only; do not use it.")
    companion object
}
