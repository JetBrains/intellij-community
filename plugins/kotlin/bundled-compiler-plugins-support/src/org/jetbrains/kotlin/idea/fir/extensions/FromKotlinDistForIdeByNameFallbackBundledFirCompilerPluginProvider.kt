// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * A provider for substituting the bundled FIR compiler plugin jars by the file name alone.
 *
 * It intentionally does not handle files which actually exist - they should have been handled by other providers.
 * It only works on files from the [KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH] folder.
 *
 * This is important for JPS projects, when the content of [KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH] folder
 * for the selected Kotlin Plugin version might not have been downloaded yet.
 *
 * Important: this is supposed to be a fallback provider, meaning that it should work as a last resort.
 */
internal class FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? {
        // this provider only handles files from 'kotlin-dist-for-ide' folder
        if (!userSuppliedPluginJar.startsWith(KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH)) return null

        // this provider should not react to non-readable (non-existing or not enough permissions) files
        if (Files.isReadable(userSuppliedPluginJar)) return null

        val suppliedJarName = userSuppliedPluginJar.name
        val matchingPlugin = KotlinK2BundledCompilerPlugins.entries.firstOrNull { it.defaultJarName == suppliedJarName }

        return matchingPlugin?.bundledJarLocation
    }
}
