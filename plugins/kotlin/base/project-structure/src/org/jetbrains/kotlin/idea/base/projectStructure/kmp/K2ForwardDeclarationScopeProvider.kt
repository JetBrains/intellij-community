// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.kmp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule

@ApiStatus.Internal
abstract class K2ForwardDeclarationScopeProvider {
    abstract fun createForwardDeclarationScope(module: KaLibraryModule): GlobalSearchScope?

    companion object {
        fun getInstance(project: Project) =
            project.service<K2ForwardDeclarationScopeProvider>()
    }
}