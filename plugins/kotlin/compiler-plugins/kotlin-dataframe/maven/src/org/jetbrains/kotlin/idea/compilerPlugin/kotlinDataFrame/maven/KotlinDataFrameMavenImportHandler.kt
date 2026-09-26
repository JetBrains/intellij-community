// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinDataFrame.maven

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import java.nio.file.Path

class KotlinDataFrameMavenImportHandler(project: Project) : AbstractMavenImportHandler(project) {
    override val compilerPluginId: String = "org.jetbrains.kotlin.dataframe"
    override val pluginName: String = "dataframe"
    override val mavenPluginArtifactName: String = "kotlin-maven-dataframe"
    override val pluginJarFileFromIdea: Path
        get() = KotlinArtifacts.kotlinDataFrameCompilerPluginPath

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<CompilerPluginSetup.PluginOption>? =
        if ("kotlin-dataframe" in enabledCompilerPlugins) emptyList() else null
}