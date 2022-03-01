// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.impl.ProjectWidePathMacroContributor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.exists
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries

const val KOTLIN_BUNDLED = "KOTLIN_BUNDLED"

class KotlinBundledPathMacroContributor : ProjectWidePathMacroContributor {
    override fun getProjectPathMacros(projectDir: String): Map<String, String> {
        // It's not possible to use KotlinJpsPluginSettings.getInstance(project) because the project isn't yet initialized
        val version = Paths.get(projectDir, Project.DIRECTORY_STORE_FOLDER, SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE)
            .takeIf { it.exists() }
            .let {
                it ?: Paths.get(projectDir).listDirectoryEntries(glob = "*.${ProjectFileType.DEFAULT_EXTENSION}").singleOrNull()
            }
            ?.let { JDOMUtil.load(it) }
            ?.children
            ?.singleOrNull { it.getAttributeValue("name") == KotlinJpsPluginSettings::class.java.simpleName }
            ?.let { xmlElement ->
                JpsPluginSettings().apply {
                    XmlSerializer.deserializeInto(this, xmlElement)
                }
            }
            ?.version ?: KotlinJpsPluginSettings.DEFAULT_VERSION
        return mapOf(KOTLIN_BUNDLED to KotlinPathsProvider.getKotlinPaths(version).homePath.canonicalPath)
    }
}