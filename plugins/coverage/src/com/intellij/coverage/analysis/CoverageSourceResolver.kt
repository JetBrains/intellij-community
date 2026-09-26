// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore

internal object CoverageSourceResolver {
  suspend fun findFile(
    project: Project,
    searchScope: GlobalSearchScope,
    className: String,
    fileName: String? = null,
  ): VirtualFile? = smartReadAction(project) {
    if (!fileName.isNullOrEmpty()) {
      val files = findFileByName(project, searchScope, className, fileName)
      files.singleOrNull()?.let { return@smartReadAction it }
    }

    findFileByClass(project, searchScope, className)
  }

  private fun findFileByName(
    project: Project,
    searchScope: GlobalSearchScope,
    className: String,
    fileName: String,
  ): List<VirtualFile> {
    val packageName = StringUtil.getPackageName(className)
    val packageIndex = PackageIndex.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    return FilenameIndex.getVirtualFilesByName(fileName, searchScope)
      .filter {
        packageIndex.getPackageName(it) == packageName ||
        // Kotlin file's package may not be correct in the package index
        (psiManager.findFile(it) as? PsiClassOwner)?.packageName == packageName
      }
  }

  private fun findFileByClass(project: Project, searchScope: GlobalSearchScope, className: String): VirtualFile? {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, searchScope)
    if (psiClass == null || !psiClass.isValid) return null
    return PsiUtilCore.getVirtualFile(psiClass.navigationElement)
  }
}
