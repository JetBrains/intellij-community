// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven.compilerPlugin

import com.intellij.openapi.project.Project
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayoutService
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.compilerPlugin.modifyCompilerArgumentsForPluginWithFacetSettings
import org.jetbrains.kotlin.idea.maven.MavenProjectImportHandler
import org.jetbrains.kotlin.idea.maven.findKotlinPlugin
import java.nio.file.Path
import kotlin.io.path.relativeTo

abstract class AbstractMavenImportHandler(protected val project: Project) : MavenProjectImportHandler {
    abstract val compilerPluginId: String
    abstract val pluginName: String
    abstract val mavenPluginArtifactName: String
    abstract val pluginJarFileFromIdea: Path

    override fun invoke(facetSettings: IKotlinFacetSettings, mavenProject: MavenProject) {
        modifyCompilerArgumentsForPluginWithFacetSettings(
            facetSettings, getPluginSetup(mavenProject),
            compilerPluginId = compilerPluginId,
            pluginName = pluginName
        )
    }

    abstract fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>?

    private fun getPluginSetup(mavenProject: MavenProject): CompilerPluginSetup? {
        val kotlinPlugin = mavenProject.findKotlinPlugin() ?: return null

        val configuration = kotlinPlugin.configurationElement ?: return null

        val enabledCompilerPlugins = configuration.getElement("compilerPlugins")
            ?.getElements()
            ?.flatMap { plugin -> plugin.content.mapNotNull { (it as? Text)?.text } }
            ?: emptyList()

        val compilerPluginOptions = configuration.getElement("pluginOptions")
            ?.getElements()
            ?.flatMap { it.content }
            ?.mapTo(mutableListOf()) { (it as Text).text }
            ?: mutableListOf<String>()

        val layoutService = KotlinPluginLayoutService.getInstance(project)
        // resolve to remote kotlinc could take some time (e.g. copying files), and it is worth having it on the import phase
        layoutService.resolveRelativeToRemoteKotlinc(pluginJarFileFromIdea)

        // We can't use the plugin from Gradle as it may have the incompatible version
        val classpath = listOf(pluginJarFileFromIdea.toJpsVersionAgnosticKotlinBundledPath())

        val options = getOptions(mavenProject, enabledCompilerPlugins, compilerPluginOptions) ?: return null
        return CompilerPluginSetup(options, classpath)
    }

    private fun Element.getElement(name: String) = content.firstOrNull { it is Element && it.name == name } as? Element

    private fun Element.getElements() = content.filterIsInstance<Element>()
}

/*
 * Bundled Kotlin compiler plugin uses Paths of bundled kotlinc into IntelliJ for build.
 * Maven (back-ended by jps) could use a specific version of kotlinc, that could be different to
 * the originally bundled.
 * `$KOTLIN_BUNDLED$` path macro is used to specify a path to the compiler plugin within provided kotlinc.
 *
 * Note: function returns a String, not a Path, to emphasize that the result contains a macro
 */
@VisibleForTesting
fun Path.toJpsVersionAgnosticKotlinBundledPath(): String {
    val kotlincDirectory = KotlinPluginLayout.kotlincPath
    require(this.startsWith(kotlincDirectory)) { "$this should start with ${kotlincDirectory}" }
    val relativePath = this.relativeTo(kotlincDirectory)
    return $$"$KOTLIN_BUNDLED$/$$relativePath"
}