// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * A provider for substituting the bundled FIR compiler plugin jars by the file name alone.
 *
 * It intentionally does not handle files which actually exist - they should have been handled by other providers.
 * It only works on files from the [KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX] folder.
 *
 * This is important for JPS projects, when the content of [KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX] folder
 * for the selected Kotlin Plugin version might not have been downloaded yet.
 *
 * Important: this is supposed to be a fallback provider, meaning that it should work as a last resort.
 */
internal class FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? {
        // this provider only handles files from 'kotlin-dist-for-ide' folder
        if (!userSuppliedPluginJar.startsWith(KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath())) return null

        // this provider should not react to non-existing files
        if (userSuppliedPluginJar.exists()) return null

        val suppliedJarName = userSuppliedPluginJar.name
        val matchingPlugin = KotlinK2BundledCompilerPlugins.entries.firstOrNull { it.defaultJarName == suppliedJarName }

        return matchingPlugin?.bundledJarLocation
    }
}

/**
 * A name of the jar corresponding to [this] compiler plugin in the [KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX] folder,
 * or `null` if it's not expected to be present there.
 */
private val KotlinK2BundledCompilerPlugins.defaultJarName: String?
    get() = when (this) {
        ALL_OPEN_COMPILER_PLUGIN -> KotlinArtifactNames.ALLOPEN_COMPILER_PLUGIN
        NO_ARG_COMPILER_PLUGIN -> KotlinArtifactNames.NOARG_COMPILER_PLUGIN
        SAM_WITH_RECEIVER_COMPILER_PLUGIN -> KotlinArtifactNames.SAM_WITH_RECEIVER_COMPILER_PLUGIN
        ASSIGNMENT_COMPILER_PLUGIN -> KotlinArtifactNames.ASSIGNMENT_COMPILER_PLUGIN
        KOTLINX_SERIALIZATION_COMPILER_PLUGIN -> KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN
        LOMBOK_COMPILER_PLUGIN -> KotlinArtifactNames.LOMBOK_COMPILER_PLUGIN
        PARCELIZE_COMPILER_PLUGIN -> KotlinArtifactNames.PARCELIZE_COMPILER_PLUGIN
        SCRIPTING_COMPILER_PLUGIN -> KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER
        DATAFRAME_COMPILER_PLUGIN -> KotlinArtifactNames.KOTLIN_DATAFRAME_COMPILER_PLUGIN

        COMPOSE_COMPILER_PLUGIN -> null
        JS_PLAIN_OBJECTS_COMPILER_PLUGIN -> null
    }