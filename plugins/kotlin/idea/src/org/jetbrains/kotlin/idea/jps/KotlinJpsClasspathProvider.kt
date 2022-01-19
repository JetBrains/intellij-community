// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings

class KotlinJpsClasspathProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> =
        SetupKotlinJpsPluginBeforeCompileTask
            .getKotlinJpsClasspathLocation(KotlinJpsPluginSettings.getInstance(project).settings.version).takeIf { it.exists() }
            ?.let { listOf(it.canonicalPath) }
            ?: emptyList()
}
