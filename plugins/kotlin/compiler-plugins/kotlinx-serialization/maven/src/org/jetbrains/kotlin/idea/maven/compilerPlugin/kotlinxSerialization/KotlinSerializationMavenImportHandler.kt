// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.idea.compilerPlugin.AnnotationBasedCompilerPluginSetup
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationImportHandler
import java.io.File

class KotlinSerializationMavenImportHandler : AbstractMavenImportHandler() {
    override val compilerPluginId: String = "org.jetbrains.kotlinx.serialization"
    override val pluginName: String = "serialization"
    override val mavenPluginArtifactName: String = "kotlin-maven-serialization"
    override val pluginJarFileFromIdea: File
        get() = File(KotlinSerializationImportHandler.PLUGIN_JPS_JAR)

    override fun getOptions(
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<AnnotationBasedCompilerPluginSetup.PluginOption>? =
        if ("kotlinx-serialization" in enabledCompilerPlugins) emptyList() else null
}