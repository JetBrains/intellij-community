// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinScopeUtils")

package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.KotlinFileType

infix fun SearchScope.and(otherScope: SearchScope): SearchScope = intersectWith(otherScope)

infix fun SearchScope.or(otherScope: SearchScope): SearchScope = union(otherScope)

infix fun GlobalSearchScope.or(otherScope: SearchScope): GlobalSearchScope = union(otherScope)

operator fun SearchScope.minus(otherScope: GlobalSearchScope): SearchScope = this and !otherScope

operator fun GlobalSearchScope.minus(otherScope: GlobalSearchScope): GlobalSearchScope = this.intersectWith(!otherScope)

operator fun GlobalSearchScope.not(): GlobalSearchScope = GlobalSearchScope.notScope(this)

fun Project.allScope(): GlobalSearchScope = GlobalSearchScope.allScope(this)

fun Project.projectScope(): GlobalSearchScope = GlobalSearchScope.projectScope(this)

fun PsiFile.fileScope(): GlobalSearchScope = GlobalSearchScope.fileScope(this)

fun PsiElement.useScope(): SearchScope = PsiSearchHelper.getInstance(project).getUseScope(this)

fun PsiElement.codeUsageScope(): SearchScope = PsiSearchHelper.getInstance(project).getCodeUsageScope(this)

fun Project.everythingScopeExcludeFileTypes(vararg fileTypes: FileType): GlobalSearchScope {
    return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.everythingScope(this), *fileTypes).not()
}

fun GlobalSearchScope.restrictByFileType(fileType: FileType) = GlobalSearchScope.getScopeRestrictedByFileTypes(this, fileType)

fun SearchScope.restrictByFileType(fileType: FileType): SearchScope = when (this) {
    is GlobalSearchScope -> restrictByFileType(fileType)
    is LocalSearchScope -> {
        val elements = scope.filter { it.containingFile.fileType == fileType }
        when (elements.size) {
            0 -> LocalSearchScope.EMPTY
            scope.size -> this
            else -> LocalSearchScope(elements.toTypedArray())
        }
    }
    else -> this
}

fun GlobalSearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.excludeFileTypes(project: Project, vararg fileTypes: FileType): SearchScope {
    return if (this is GlobalSearchScope) {
        this.intersectWith(project.everythingScopeExcludeFileTypes(*fileTypes))
    } else {
        this as LocalSearchScope
        val filteredElements = scope.filter { it.containingFile.fileType !in fileTypes }
        if (filteredElements.isNotEmpty())
            LocalSearchScope(filteredElements.toTypedArray())
        else
            LocalSearchScope.EMPTY
    }
}

fun SearchScope.excludeKotlinSources(project: Project): SearchScope = excludeFileTypes(project, KotlinFileType.INSTANCE)
