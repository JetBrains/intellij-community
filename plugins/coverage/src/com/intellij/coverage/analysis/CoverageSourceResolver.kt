// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiUtilCore

internal object CoverageSourceResolver {
  suspend fun findClass(project: Project, suite: CoverageSuitesBundle, topLevelClassName: String): CoverageSource? = smartReadAction(project) {
    if (project.isDisposed) return@smartReadAction null
    val psiClass = JavaPsiFacade.getInstance(project).findClass(topLevelClassName, suite.getSearchScope(project))
    if (psiClass == null || !psiClass.isValid) return@smartReadAction null
    if (!suite.coverageEngine.acceptedByFilters(psiClass.containingFile, suite)) return@smartReadAction null

    val sourceFile = PsiUtilCore.getVirtualFile(psiClass.navigationElement)
    CoverageSource(psiClass, sourceFile)
  }
}

internal data class CoverageSource(val psiClass: PsiClass, val file: VirtualFile?)
