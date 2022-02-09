// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.compilerPlugin

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.compilerPlugin.modifyCompilerArgumentsForPlugin
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.io.File

abstract class AbstractAnnotationBasedCompilerPluginGradleImportHandler<T : AnnotationBasedPluginModel> : AbstractCompilerPluginGradleImportHandler<T>() {
    abstract val annotationOptionName: String

    protected open fun getAnnotationsForPreset(presetName: String): List<String> = emptyList()

    override fun isEnabled(model: T): Boolean {
        return model.isEnabled
    }

    override fun getOptions(model: T): List<PluginOption> {
        val annotations = model.annotations
        val presets = model.presets

        val allAnnotations = annotations + presets.flatMap { getAnnotationsForPreset(it) }
        return allAnnotations.map { PluginOption(annotationOptionName, it) }
    }
}

abstract class AbstractCompilerPluginGradleImportHandler<T> : GradleProjectImportHandler {
    abstract val compilerPluginId: String
    abstract val pluginName: String
    abstract val pluginJarFileFromIdea: File
    abstract val modelKey: Key<T>

    override fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>) {
        modifyCompilerArgumentsForPlugin(facet, getPluginSetupBySourceSet(sourceSetNode),
                                         compilerPluginId = compilerPluginId,
                                         pluginName = pluginName)
    }

    override fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>) {
        modifyCompilerArgumentsForPlugin(facet, getPluginSetupByModule(moduleNode),
                                         compilerPluginId = compilerPluginId,
                                         pluginName = pluginName)
    }

    protected open fun isEnabled(model: T): Boolean = true

    protected open fun getOptions(model: T): List<PluginOption> = emptyList()

    private fun getPluginSetupByModule(moduleNode: DataNode<ModuleData>): CompilerPluginSetup? {
        val model = moduleNode.getCopyableUserData(modelKey)?.takeIf { isEnabled(it) } ?: return null
        val options = getOptions(model)

        // For now we can't use plugins from Gradle cause they're shaded and may have an incompatible version.
        // So we use ones from the IDEA plugin.
        val classpath = listOf(pluginJarFileFromIdea.absolutePath)

        return CompilerPluginSetup(options, classpath)
    }

    private fun getPluginSetupBySourceSet(sourceSetNode: DataNode<GradleSourceSetData>) =
            ExternalSystemApiUtil.findParent(sourceSetNode, ProjectKeys.MODULE)?.let { getPluginSetupByModule(it) }
}
