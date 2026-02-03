// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinDataFrame.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.jps.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler

class KotlinDataFrameMavenImportHandler : AbstractMavenImportHandler() {
    override val compilerPluginId: String = "org.jetbrains.kotlin.dataframe"
    override val pluginName: String = "dataframe"
    override val mavenPluginArtifactName: String = "kotlin-maven-dataframe"
    override val pluginJarFileFromIdea: String
        get() = KotlinArtifacts.kotlinDataFrameCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<CompilerPluginSetup.PluginOption>? =
        if ("kotlin-dataframe" in enabledCompilerPlugins) emptyList() else null
}