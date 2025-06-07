// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import kotlin.io.path.absolutePathString
import com.intellij.openapi.module.Module as OpenapiModule

internal fun <T> OpenapiModule.withCompilerPlugin(
    plugin: KotlinK2BundledCompilerPlugins,
    options: String? = null,
    action: () -> T
): T {
    val pluginJar = plugin.bundledJarLocation

    return withCustomCompilerOptions(
        "// COMPILER_ARGUMENTS: -Xplugin=${pluginJar.absolutePathString()} ${options?.let { "-P $it" }.orEmpty()}",
        project,
        module = this
    ) {
        action()
    }
}