// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.platform.tooling
import org.jetbrains.kotlin.platform.IdePlatformKind

class KotlinVersionInfoProviderByModuleDependencies : KotlinVersionInfoProvider {
    override fun getCompilerVersion(module: Module) = KotlinPluginLayout.instance.standaloneCompilerVersion

    override fun getLibraryVersions(
        module: Module,
        platformKind: IdePlatformKind,
        rootModel: ModuleRootModel?
    ): Collection<IdeKotlinVersion> {
        if (module.isDisposed) {
            return emptyList()
        }

        val versionProvider = platformKind.tooling.getLibraryVersionProvider(module.project)
        val orderEntries = (rootModel ?: ModuleRootManager.getInstance(module)).orderEntries

        return mutableListOf<IdeKotlinVersion>().apply {
            for (orderEntry in orderEntries) {
                val library = (orderEntry as? LibraryOrderEntry)?.library ?: continue
                val version = versionProvider(library) ?: continue
                add(version)
            }
        }
    }
}