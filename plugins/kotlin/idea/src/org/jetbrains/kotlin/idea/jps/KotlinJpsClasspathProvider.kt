// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout

class KotlinJpsClasspathProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> {
        val jpsPluginClasspath = KotlinJpsPluginSettings.jpsVersion(project)
            ?.let { KotlinArtifactsDownloader.getKotlinJpsPluginJarPath(it) }
            ?: KotlinPluginLayout.instance.jpsPluginJar

        return listOf(jpsPluginClasspath.canonicalPath)
    }
}
