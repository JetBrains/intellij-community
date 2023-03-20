// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathUtil
import com.intellij.util.io.isDirectory
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinJpsPluginClasspathDownloader
import java.nio.file.Path

class KotlinJpsBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> {
        val version = KotlinJpsPluginSettings.jpsVersion(project)
        val jpsPluginClasspath = LazyKotlinJpsPluginClasspathDownloader(version).getDownloadedIfUpToDateOrEmpty()
        return jpsPluginClasspath.map { it.canonicalPath } + listOf(PathUtil.getJarPathForClass(com.intellij.util.PathUtil::class.java))
    }

    override fun getPathParameters(): List<Pair<String, Path>> = listOfNotNull(
        Pair("-Djps.kotlin.home=", KotlinArtifactsDownloader.getUnpackedKotlinDistPath(project).toPath()).takeIf { it.second.isDirectory() }
    )

    override fun getVMArguments(): List<String> =
        if (Registry.`is`("kotlin.jps.instrument.bytecode", false))
            listOf("-Dkotlin.jps.instrument.bytecode=true")
        else
            emptyList()
}
