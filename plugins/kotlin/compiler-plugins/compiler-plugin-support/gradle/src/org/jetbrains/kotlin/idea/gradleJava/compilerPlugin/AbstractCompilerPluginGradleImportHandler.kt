// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.compilerPlugin

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.nio.file.Path

abstract class AbstractGradleImportHandler : GradleProjectImportHandler {
    abstract val pluginJarsRegex: List<Regex>
    abstract val replacedJar: Path

    override fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>) {
        processCompilerPluginClasspath(facet, pluginJarsRegex, replacedJar)
    }

    override fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>) {
        processCompilerPluginClasspath(facet, pluginJarsRegex, replacedJar)
    }

    private fun processCompilerPluginClasspath(
        facet: KotlinFacet,
        pluginJarsRegex: List<Regex>,
        replacedJar: Path,
    ) {
        val facetSettings = facet.configuration.settings
        facetSettings.updateCompilerArguments {
            var isAlreadyReplaced = false

            val newPluginClasspaths = buildList {
                for (jarFile in this@updateCompilerArguments.pluginClasspaths ?: emptyArray<String>()) {
                    val jarFileName = Path.of(jarFile).fileName.toString()
                    val matches = pluginJarsRegex.any { regex -> jarFileName.matches(regex) }
                    if (matches) {
                        if (!isAlreadyReplaced) {
                            // replace only first occurrence
                            add(replacedJar.toString())
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
}
