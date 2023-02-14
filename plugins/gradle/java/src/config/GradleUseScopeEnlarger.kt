// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * @author Vladislav.Soroka
 */
class GradleUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    return try {
      getScope(element)
    }
    catch (e: IndexNotReadyException) {
      null
    }

  }

  companion object {
    private fun getScope(element: PsiElement): SearchScope? {
      val virtualFile = PsiUtilCore.getVirtualFile(element.containingFile) ?: return null
      val project = element.project

      if (!isInBuildSrc(project, virtualFile) && !isInGradleDistribution(project, virtualFile)) return null


      return GradleBuildscriptSearchScope(element.project)
    }

    private fun isInGradleDistribution(project: Project, file: VirtualFile) : Boolean {
      val gradleClassFinder = PsiElementFinder.EP.findExtension(GradleClassFinder::class.java, project) ?: return false
      val roots = gradleClassFinder.calcClassRoots()
      for (root in roots) {
        if (VfsUtilCore.isAncestor(root, file, true)) {
          return true
        }
      }
      return false
    }

    private fun isInBuildSrc(project: Project, file: VirtualFile) : Boolean {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      val module = fileIndex.getModuleForFile(file) ?: return false
      if (!isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
        return false
      }
      val rootProjectPath = getExternalRootProjectPath(module) ?: return false
      return isApplicable(project, module, rootProjectPath, file, fileIndex)
    }

    private fun isApplicable(project: Project,
                             module: Module,
                             rootProjectPath: String,
                             virtualFile: VirtualFile,
                             fileIndex: ProjectFileIndex): Boolean {
      val projectPath = getExternalProjectPath(module) ?: return false
      if (projectPath.endsWith("/buildSrc")) return true
      val sourceRoot = fileIndex.getSourceRootForFile(virtualFile)
      return sourceRoot in GradleBuildClasspathManager.getInstance(project).getModuleClasspathEntries(rootProjectPath)
    }

    fun search(element: PsiMember, consumer: Processor<PsiReference>) {
      val scope: SearchScope = ReadAction.compute<SearchScope, RuntimeException> { getScope(element) } ?: return
      val newParams = ReferencesSearch.SearchParameters(element, scope, true)
      ReferencesSearch.search(newParams).forEach(consumer)
    }
  }
}
