// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.base.psi.userDataCached
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.resolve.KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.resolve.loadSingleJarFromMaven
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.io.File
import kotlin.io.path.absolutePathString
import com.intellij.openapi.module.Module as OpenapiModule

internal fun <T> OpenapiModule.withCompilerPlugin(
    plugin: KotlinK2BundledCompilerPlugins,
    options: String? = null,
    action: () -> T
): T {
    val pluginJar = plugin.bundledJarLocation

    when (plugin) {
        KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN -> {
            ConfigLibraryUtil.addLibrary(this, "serialization-core") {
                addRoot(project.serializationCoreJar, OrderRootType.CLASSES)
            }
        }

        else -> {}
    }

    return withCustomCompilerOptions(
        "// COMPILER_ARGUMENTS: -Xplugin=${pluginJar.absolutePathString()} ${options?.let { "-P $it" }.orEmpty()}",
        project,
        module = this
    ) {
        action()
    }
}

private val Project.serializationCoreJar: File by userDataCached("KOTLINX_SERIALIZATION_FILE", { 0 }) { project: Project ->
    project.loadSingleJarFromMaven(KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES)
}
