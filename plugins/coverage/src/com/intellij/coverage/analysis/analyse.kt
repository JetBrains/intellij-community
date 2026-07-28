// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageSuite
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

internal suspend fun collectOutputRoots(bundle: CoverageSuitesBundle, project: Project): Map<ModuleRequest, List<RequestRoot>> {
  val coverageDataManager = CoverageDataManager.getInstance(project)

  val javaSuites = bundle.suites.filterIsInstance<JavaCoverageSuite>()
  val packageNames = readAction {
    javaSuites.flatMap { it.getCurrentSuitePackages(project).mapNotNull { psiPackage -> psiPackage.qualifiedName } }
  }
    .removeSubPackages()
    .filter { isPackageFiltered(bundle, it) }
  val classesWithNames = readAction {
    javaSuites.flatMap { it.getCurrentSuiteClasses(project) }
      .distinct()
      .mapNotNull { psiClass -> psiClass.qualifiedName?.let { psiClass to it } }
      .filter { (_, className) -> packageNames.none { className.startsWith(it) } }
  }
  val modules = readAction {
    if (packageNames.isNotEmpty()) {
      ModuleManager.getInstance(project).modules.toList()
    }
    else {
      classesWithNames.mapNotNull { (psiClass, _) -> ModuleUtilCore.findModuleForPsiElement(psiClass) }.distinct()
    }
  }
    .filter { bundle.getSearchScope(project).isSearchInModuleContent(it) }

  val outputRoots = modules.flatMap { module ->
    CoverageOutputRoots.getRoots(coverageDataManager, module, bundle.isTrackTestFolders).toList().map { it to module }
  }.distinct()

  val requestedPackages = packageNames.map { it to null }
    .plus(classesWithNames.map { (_, fqn) -> StringUtil.getPackageName(fqn) to StringUtil.getShortName(fqn) })

  val roots = hashMapOf<ModuleRequest, MutableList<RequestRoot>>()
  for ((root, module) in outputRoots) {
    val outputRoot = root.toNioPath()
    for ((packageName, simpleName) in requestedPackages) {
      val packagePath = AnalysisUtils.fqnToInternalName(packageName)
      val isValidRoot = Files.isDirectory(outputRoot) || Files.isRegularFile(outputRoot) && outputRoot.fileName.toString()
        .endsWith(".jar", ignoreCase = true)
      if (isValidRoot) {
        val requestRoot = RequestRoot(outputRoot, simpleName, packagePath)
        roots.getOrPut(ModuleRequest(packageName, module)) { mutableListOf() }.add(requestRoot)
      }
    }
  }
  return roots
}

internal data class ModuleRequest(val packageName: String, val module: Module)
internal data class RequestRoot(val root: Path, val simpleName: String?, val packagePathInRoot: String)

@ApiStatus.Internal
object CoverageOutputRoots {
  @JvmStatic
  fun getRoots(manager: CoverageDataManager, module: Module, includeTests: Boolean): Array<VirtualFile> {
    val roots = manager.doInReadActionIfProjectOpen {
      var enumerator = OrderEnumerator.orderEntries(module)
        .withoutSdk()
        .withoutLibraries()
        .withoutDepModules()
      if (!includeTests) enumerator = enumerator.productionOnly()
      enumerator.classes().roots
    }
    return roots ?: VirtualFile.EMPTY_ARRAY
  }
}

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
