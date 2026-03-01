// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen.maven

import com.intellij.openapi.project.Project
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.SUPPORTED_PRESETS
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import java.nio.file.Path
import org.jetbrains.kotlin.idea.maven.getKotlinPlugin

class AllOpenMavenProjectImportHandler(project: Project) : AbstractMavenImportHandler(project) {
    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = "allopen"
    override val mavenPluginArtifactName: String = "kotlin-maven-allopen"
    override val pluginJarFileFromIdea: Path = KotlinArtifacts.allopenCompilerPluginPath

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("all-open" !in enabledCompilerPlugins &&
            "spring" !in enabledCompilerPlugins &&
            !mavenProject.isJpaWithAllOpenEnabled(enabledCompilerPlugins)
        ) {
            return null
        }

        val annotations = mutableListOf<String>()

        for ((presetName, presetAnnotations) in SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        return annotations.map { PluginOption(ANNOTATION_OPTION_NAME, it) }
    }

    private fun MavenProject.isJpaWithAllOpenEnabled(
        enabledCompilerPlugins: List<String>
    ): Boolean {
        val kotlinPluginVersion = getKotlinPlugin().version

        return "jpa" in enabledCompilerPlugins &&
                VersionComparatorUtil.compare(kotlinPluginVersion, "2.3.20-Beta2") >= 0
    }
}

private const val ANNOTATION_PARAMETER_PREFIX = "all-open:$ANNOTATION_OPTION_NAME="
