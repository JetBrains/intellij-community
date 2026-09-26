// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * A main implementation of [KotlinBundledFirCompilerPluginProvider].
 *
 * Provides compiler plugins listed in [KotlinK2BundledCompilerPlugins] based on the content of the registrar's content which is read
 * directly from the jar.
 */
internal class MainByRegistrarContentBundledFirCompilerPluginProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(project: Project, userSuppliedPluginJar: Path): Path? {
        val registrarContent = CompilerPluginRegistrarUtils.readRegistrarContent(userSuppliedPluginJar) ?: return null
        val matchingPlugin = KotlinK2BundledCompilerPlugins.entries.firstOrNull { it.registrarClassName in registrarContent }

        return matchingPlugin?.bundledJarLocation
    }
}
