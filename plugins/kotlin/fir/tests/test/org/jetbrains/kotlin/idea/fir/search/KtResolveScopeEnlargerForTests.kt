// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger

class KtResolveScopeEnlargerForTests(private val virtualFile: VirtualFile) : KotlinResolveScopeEnlarger {
    override fun getAdditionalResolveScope(module: Module, isTestScope: Boolean): SearchScope {
        return object : GlobalSearchScope(module.project) {
            override fun contains(file: VirtualFile): Boolean = file == virtualFile

            override fun isSearchInModuleContent(aModule: Module): Boolean = false

            override fun isSearchInLibraries(): Boolean = false
        }
    }
}