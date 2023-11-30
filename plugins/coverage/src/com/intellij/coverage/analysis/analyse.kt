// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageSuite
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File

internal fun collectOutputRoots(bundle: CoverageSuitesBundle, project: Project): Map<ModuleRequest, List<RequestRoot>> {
  val coverageDataManager = CoverageDataManager.getInstance(project)

  val javaSuites = bundle.suites.filterIsInstance<JavaCoverageSuite>()
  val packageNames = javaSuites.flatMap { it.getCurrentSuitePackages(project).mapNotNull { p -> runReadAction { p.qualifiedName } } }
    .removeSubPackages()
    .filter { isPackageFiltered(bundle, it) }
  val classes = javaSuites
    .flatMap { it.getCurrentSuiteClasses(project) }.distinct()
    .filter { aClass ->
      val className = runReadAction { aClass.qualifiedName }
      className != null && packageNames.none { className.startsWith(it) }
    }
  val modules = (if (packageNames.isNotEmpty()) {
    coverageDataManager.doInReadActionIfProjectOpen { ModuleManager.getInstance(project).modules }?.toList() ?: emptyList()
  }
  else {
    classes.mapNotNull { runReadAction { ModuleUtilCore.findModuleForPsiElement(it) } }.distinct()
  })
    .filter { bundle.getSearchScope(project).isSearchInModuleContent(it) }

  val outputRoots = modules.flatMap { module ->
    JavaCoverageClassesEnumerator.getRoots(coverageDataManager, module, bundle.isTrackTestFolders).toList().map { it to module }
  }.distinct()

  val requestedPackages = packageNames.map { it to null }
    .plus(classes.mapNotNull { aClass ->
      val fqn = runReadAction { aClass.qualifiedName }
      fqn?.let { StringUtil.getPackageName(it) to StringUtil.getShortName(it) }
    })

  val roots = hashMapOf<ModuleRequest, MutableList<RequestRoot>>()
  for ((root, module) in outputRoots) {
    for ((packageName, simpleName) in requestedPackages) {
      val packagePath = AnalysisUtils.fqnToInternalName(packageName)
      val packageRoot = PackageAnnotator.findRelativeFile(packagePath, VfsUtilCore.virtualToIoFile(root))
      if (packageRoot.exists()) {
        roots.getOrPut(ModuleRequest(packageName, module)) { mutableListOf() }.add(RequestRoot(packageRoot, simpleName))
      }
    }
  }
  return roots
}

internal data class ModuleRequest(val packageName: String, val module: Module)
internal data class RequestRoot(val root: File, val simpleName: String?)

private fun List<String>.removeSubPackages(): List<String> {
  val allPackages = this.sortedBy { it.length }
  val packages = mutableListOf<String>()
  for (fqn in allPackages) {
    if (packages.none { it.startsWith(fqn) }) {
      packages.add(fqn)
    }
  }
  return packages
}

private fun isPackageFiltered(bundle: CoverageSuitesBundle, qualifiedName: String): Boolean {
  for (coverageSuite in bundle.suites) {
    if (coverageSuite is JavaCoverageSuite && coverageSuite.isPackageFiltered(qualifiedName)) {
      return true
    }
  }
  return false
}
