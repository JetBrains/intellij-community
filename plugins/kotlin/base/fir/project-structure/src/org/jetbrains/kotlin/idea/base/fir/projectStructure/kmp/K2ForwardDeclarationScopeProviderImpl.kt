// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.kmp

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaLibraryModuleImpl
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.K2ForwardDeclarationScopeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.K2ModuleInfoBasedForwardDeclarationScopeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.symbolicId
import org.jetbrains.kotlin.idea.base.projectStructure.useNewK2ProjectStructureProvider
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

internal class K2ForwardDeclarationScopeProviderImpl(private val project: Project) : K2ForwardDeclarationScopeProvider() {
    override fun createForwardDeclarationScope(module: KaLibraryModule): GlobalSearchScope? {
        if (useNewK2ProjectStructureProvider) {
            if (module !is KaLibraryModuleImpl) return null
            return org.jetbrains.kotlin.idea.base.projectStructure.kmp.createForwardDeclarationScope(module.symbolicId, module, project)
        } else {
            @OptIn(K1ModeProjectStructureApi::class)
            return K2ModuleInfoBasedForwardDeclarationScopeProvider().createForwardDeclarationScope(module)
        }
    }
}