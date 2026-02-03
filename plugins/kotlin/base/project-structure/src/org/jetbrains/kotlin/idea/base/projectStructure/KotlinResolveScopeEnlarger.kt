// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope

interface KotlinResolveScopeEnlarger {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinResolveScopeEnlarger> =
            ExtensionPointName.create("org.jetbrains.kotlin.resolveScopeEnlarger")

        fun enlargeScope(scope: GlobalSearchScope, file: PsiFile): GlobalSearchScope {
            val virtualFile = file.originalFile.virtualFile ?: return scope
            val context = file.originalFile.codeInsightContext

            var result = scope
            for (extension in ResolveScopeEnlarger.EP_NAME.extensions) {
                val project = scope.project ?: continue
                val additionalScope = extension.getAdditionalResolveScope(virtualFile, context, project) ?: continue
                result = result.union(additionalScope)
            }
            return result
        }

        fun enlargeScope(scope: GlobalSearchScope, module: Module, isTestScope: Boolean): GlobalSearchScope {
            var result = scope
            for (extension in EP_NAME.extensions) {
                val additionalScope = extension.getAdditionalResolveScope(module, isTestScope) ?: continue
                result = result.union(additionalScope)
            }
            return result
        }
    }

    fun getAdditionalResolveScope(module: Module, isTestScope: Boolean): SearchScope?
}