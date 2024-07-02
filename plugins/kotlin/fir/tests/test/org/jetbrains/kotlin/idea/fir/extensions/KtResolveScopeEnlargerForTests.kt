// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.extensions

import com.intellij.codeInsight.completion.BaseCompletionService
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.SearchScope
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger

const val TYPE_NAME_FOR_ENLARGED_SCOPE_TEST = "TypeFromScopeEnlarger"

object CompletionServiceForScopeEnlargerTest : BaseCompletionService() {
    override fun createMatcher(prefix: String?, typoTolerant: Boolean): PrefixMatcher {
        return object : PrefixMatcher(TYPE_NAME_FOR_ENLARGED_SCOPE_TEST) {
            override fun prefixMatches(name: String): Boolean {
                return TYPE_NAME_FOR_ENLARGED_SCOPE_TEST.startsWith(name)
            }

            override fun cloneWithPrefix(prefix: String): PrefixMatcher {
                return this
            }
        }
    }
}

class KtResolveScopeEnlargerForTests(private val virtualFile: VirtualFile) : KotlinResolveScopeEnlarger {
    override fun getAdditionalResolveScope(module: Module, isTestScope: Boolean): SearchScope {
        return object : GlobalSearchScope(module.project) {
            override fun contains(file: VirtualFile): Boolean = file == virtualFile

            override fun isSearchInModuleContent(aModule: Module): Boolean = false

            override fun isSearchInLibraries(): Boolean = false
        }
    }
}

private const val STRING_TRIGGER_TYPE_COMPLETION = "WrongType"

class ShortNamesCacheForScopeEnlargerTests(private val psiClass: PsiClass) : PsiShortNamesCache() {
    override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
        if (!STRING_TRIGGER_TYPE_COMPLETION.startsWith(name)) return emptyArray()
        return if (PsiSearchScopeUtil.isInScope(scope, psiClass)) {
            arrayOf(psiClass)
        } else {
            emptyArray()
        }
    }

    override fun getAllClassNames(): Array<String> = arrayOf(STRING_TRIGGER_TYPE_COMPLETION)

    override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> = emptyArray()

    override fun getMethodsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiMethod> = emptyArray()

    override fun getFieldsByNameIfNotMoreThan(name: String, scope: GlobalSearchScope, maxCount: Int): Array<PsiField> = emptyArray()

    override fun processMethodsWithName(name: String, scope: GlobalSearchScope, processor: Processor<in PsiMethod>): Boolean = false

    override fun getAllMethodNames(): Array<String> = emptyArray()

    override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> = emptyArray()

    override fun getAllFieldNames(): Array<String> = emptyArray()
}