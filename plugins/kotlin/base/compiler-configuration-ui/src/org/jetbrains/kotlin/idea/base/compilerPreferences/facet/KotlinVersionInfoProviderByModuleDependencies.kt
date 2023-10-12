// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.compilerPreferences.facet

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import org.jetbrains.kotlin.idea.base.platforms.IdePlatformKindProjectStructure
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.KotlinVersionInfoProvider
import org.jetbrains.kotlin.platform.IdePlatformKind

class KotlinVersionInfoProviderByModuleDependencies : KotlinVersionInfoProvider {
    override fun getCompilerVersion(module: Module) = KotlinPluginLayout.standaloneCompilerVersion

    override fun getCompilerVersion(): IdeKotlinVersion = KotlinPluginLayout.standaloneCompilerVersion

    override fun getLibraryVersions(
        module: Module,
        platformKind: IdePlatformKind,
        rootModel: ModuleRootModel?
    ): Collection<IdeKotlinVersion> = getLibraryVersionsSequence(module, platformKind, rootModel).toList()

    override fun getLibraryVersionsSequence(
        module: Module,
        platformKind: IdePlatformKind,
        rootModel: ModuleRootModel?
    ): Sequence<IdeKotlinVersion> {
        if (module.isDisposed) {
            return emptySequence()
        }

        val versionProvider = IdePlatformKindProjectStructure.getInstance(module.project).getLibraryVersionProvider(platformKind)
        val orderEntries = (rootModel ?: ModuleRootManager.getInstance(module)).orderEntries

        return sequence {
            for (orderEntry in orderEntries) {
                val library = (orderEntry as? LibraryOrderEntry)?.library ?: continue
                val version = versionProvider(library) ?: continue
                yield(version)
            }
        }
    }
}