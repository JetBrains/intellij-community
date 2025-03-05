// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.kmp

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.idea.base.projectStructure.KtNativeKlibLibraryModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

@ApiStatus.Internal
@OptIn(K1ModeProjectStructureApi::class)
class K2ModuleInfoBasedForwardDeclarationScopeProvider : K2ForwardDeclarationScopeProvider() {
    override fun createForwardDeclarationScope(module: KaLibraryModule): GlobalSearchScope? {
        if (module !is KtNativeKlibLibraryModuleByModuleInfo) return null
        return module.forwardDeclarationsScope
    }
}