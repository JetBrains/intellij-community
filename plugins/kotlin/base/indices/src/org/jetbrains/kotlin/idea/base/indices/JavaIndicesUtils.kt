// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JavaIndicesUtils")

package org.jetbrains.kotlin.idea.base.indices

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

fun findModuleInfoFile(project: Project, scope: GlobalSearchScope): PsiJavaModule? {
    val psiManager = PsiManager.getInstance(project)

    return FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, scope)
        .asSequence()
        .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
        .firstNotNullOfOrNull { it.moduleDeclaration }
}