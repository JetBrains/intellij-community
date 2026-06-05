// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.compilerPlugin

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.nio.file.Path

abstract class AbstractGradleImportHandler : GradleProjectImportHandler {
    abstract val pluginJarsToReplaceRegex: List<Regex>
    open val replacementArtifactCoordinates: MavenCoordinates? = null
    abstract val replacementJarFromPluginBundle: Path

    override fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>) {
        processCompilerPluginClasspath(facet)
    }

    override fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>) {
        processCompilerPluginClasspath(facet)
    }

    private fun processCompilerPluginClasspath(facet: KotlinFacet) {
        val project = facet.module.project
        val facetSettings = facet.configuration.settings
        facetSettings.updateCompilerArguments {
            var isAlreadyReplaced = false

            val newPluginClasspaths = buildList {
                for (jarFile in this@updateCompilerArguments.pluginClasspaths) {
                    val jarFileName = Path.of(jarFile).fileName.toString()
                    val matches = pluginJarsToReplaceRegex.any { regex -> jarFileName.matches(regex) }
                    if (matches) {
                        if (!isAlreadyReplaced) {
                            // replace only first occurrence
                            add(resolveSubstituteJar(project, jarFile).toString())
                            isAlreadyReplaced = true
                        } else {
                            // we do not expect several matching jars in classpath,
                            // but if it happens, we just skip them
                            continue
                        }
                    } else {
                        // no need to modify -- leave as it is
                        add(jarFile)
                    }
                }
            }
            this.pluginClasspaths = newPluginClasspaths.toTypedArray()
        }
    }

    private fun resolveSubstituteJar(project: Project, jarFile: String): Path {
        val coordinates = replacementArtifactCoordinates ?: return replacementJarFromPluginBundle
        val version = extractVersionFromMavenLayout(jarFile) ?: return replacementJarFromPluginBundle
        return KotlinArtifactsDownloader.resolveProjectCompilerPluginArtifact(
            project, coordinates.groupId, coordinates.artifactId, version,
        ) ?: replacementJarFromPluginBundle
    }

    private fun extractVersionFromMavenLayout(jarFile: String): String? {
        val fileName = Path.of(jarFile).fileName?.toString() ?: return null
        var versionDir: Path? = Path.of(jarFile).parent
        while (versionDir != null) {
            val versionCandidate = versionDir.fileName?.toString()
            val artifactCandidate = versionDir.parent?.fileName?.toString()
            if (versionCandidate != null && artifactCandidate != null &&
                fileName.startsWith("$artifactCandidate-$versionCandidate")
            ) {
                return versionCandidate
            }
            versionDir = versionDir.parent
        }
        return null
    }
}

data class MavenCoordinates(val groupId: String, val artifactId: String)
