// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.gradleJava

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationImportHandler
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class KotlinSerializationGradleImportHandler : GradleProjectImportHandler {
    override fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>) {
        KotlinSerializationImportHandler.modifyCompilerArguments(facet, pluginJarRegex)
    }

    override fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>) {
        KotlinSerializationImportHandler.modifyCompilerArguments(facet, pluginJarRegex)
    }

    private val pluginJarRegex = listOf(
        "$PLUGIN_COMPILER_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex(),
        "$PLUGIN_COMPILER_JAR_NAME-.*\\.jar".toRegex(),
        "$PLUGIN_GRADLE_JAR_NAME-.*\\.jar".toRegex()
    )

    companion object {
        private const val PLUGIN_GRADLE_JAR_NAME = "kotlin-serialization"
        private const val PLUGIN_COMPILER_EMBEDDABLE_JAR_NAME = "kotlinx-serialization-compiler-plugin-embeddable"
        private const val PLUGIN_COMPILER_JAR_NAME = "kotlinx-serialization-compiler-plugin"
    }
}