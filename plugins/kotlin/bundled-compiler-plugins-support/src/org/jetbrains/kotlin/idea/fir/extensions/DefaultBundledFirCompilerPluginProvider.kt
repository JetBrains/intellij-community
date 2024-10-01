// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import java.nio.file.Path

/**
 * A default implementation of [KotlinBundledFirCompilerPluginProvider].
 *
 * Provides compiler plugins listed in [KotlinK2BundledCompilerPlugins].
 */
internal class DefaultBundledFirCompilerPluginProvider : KotlinBundledFirCompilerPluginProvider {
    override fun provideBundledPluginJar(userSuppliedPluginJar: Path): Path? {
        return KotlinK2BundledCompilerPlugins.findCorrespondingBundledPlugin(userSuppliedPluginJar)?.bundledJarLocation
    }
}
