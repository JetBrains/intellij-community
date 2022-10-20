// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.nio.file.Path
import kotlin.io.path.extension

const val KOTLIN_BUNDLED: String = "KOTLIN_BUNDLED"

private class KotlinBundledPathMacroContributor : ProjectWidePathMacroContributor {
    override fun getProjectPathMacros(projectFilePath: String): Map<String, String> {
        // It's not possible to use KotlinJpsPluginSettings.getInstance(project) because the project isn't yet initialized
        val path = Path.of(projectFilePath)
            .let { iprOrMisc ->
                when (iprOrMisc.extension) {
                    ProjectFileType.DEFAULT_EXTENSION -> iprOrMisc
                    "xml" -> iprOrMisc.resolveSibling(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
                    else -> error("projectFilePath should be either misc.xml or *.ipr file")
                }
            }
            ?.let { KotlinJpsPluginSettings.readFromKotlincXmlOrIpr(it) }
            ?.version
            ?.let { KotlinArtifactsDownloader.getUnpackedKotlinDistPath(it).canonicalPath }
            ?: KotlinPluginLayout.kotlinc.canonicalPath
        return mapOf(KOTLIN_BUNDLED to path)
    }
}
