// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinFacetSettings

/**
 * The classpath of the Kotlin JPS builder that the IDE bundles.
 *
 * The build process gets the builder, the facet settings, the compiler arguments and the Build Tools API. The Build
 * Tools API implementation of the project's Kotlin version is not on this classpath: the builder loads it from the
 * directory in the `kotlin.jps.build.tools.impl.home` system property.
 *
 * The builder module `intellij.kotlin.builder.jps` is a content module of the Kotlin plugin. This module does not depend
 * on it: a product without the JPS build system, such as the language server, bundles this module without the builder.
 * The builder classes are located by name and never initialized here.
 */
internal object KotlinJpsBuilderClasspath {
    /** One class per jar. The class only locates the jar on disk. */
    private val jarMarkers: List<Class<*>> = listOf(
        KotlinFacetSettings::class.java, // intellij.kotlin.jps.util.runtime
        K2JVMCompilerArguments::class.java, // kotlin-compiler-common-for-ide, which also holds the Build Tools API
        kotlin.reflect.full.NoSuchPropertyException::class.java, // kotlin-reflect, which the facet settings serialization uses
        com.intellij.util.PathUtil::class.java, // intellij.platform.util, as the legacy JPS plugin gets it
    )

    /** One class per jar that this module does not depend on. The class loader of [BUILDER_MODULE] sees them all. */
    private val jarMarkerNames: List<String> = listOf(
        "org.jetbrains.kotlin.jps.build.KotlinBuilderService", // intellij.kotlin.builder.jps
        "org.jetbrains.kotlin.buildtools.api.jps.jvm.JvmJpsManagedIncrementalCompilationConfiguration", // kotlin-build-tools-api-jps
    )

    /**
     * The content module of the builder. It loads only with the backend, because its dependencies on the JPS build
     * system load only with the backend.
     */
    private val BUILDER_MODULE = PluginModuleId("intellij.kotlin.builder.jps", PluginModuleId.JETBRAINS_NAMESPACE)

    fun get(): List<String> {
        val classLoader = PluginManagerCore.getPluginSet().findEnabledModule(BUILDER_MODULE)?.pluginClassLoader
                          ?: error("The Kotlin JPS builder is not bundled in this product: ${BUILDER_MODULE.name} is not enabled")
        val markers = jarMarkers + jarMarkerNames.map { name ->
            try {
                Class.forName(name, false, classLoader)
            }
            catch (e: ClassNotFoundException) {
                throw IllegalStateException("The Kotlin JPS builder is not bundled in this product: $name is missing", e)
            }
        }
        return markers
            .map { PathManager.getJarPathForClass(it) ?: error("Cannot locate the jar that holds ${it.name}") }
            .distinct()
    }
}
