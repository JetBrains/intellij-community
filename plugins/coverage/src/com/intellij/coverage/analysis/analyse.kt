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

internal suspend fun collectOutputRoots(bundle: CoverageSuitesBundle, project: Project): List<ModuleRequest> {
  val coverageDataManager = CoverageDataManager.getInstance(project)

  val javaSuites = bundle.suites.filterIsInstance<JavaCoverageSuite>()
  val packageNames = readAction {
    javaSuites.flatMap { it.getCurrentSuitePackages(project).mapNotNull { psiPackage -> psiPackage.qualifiedName } }
  }.filter { isPackageFiltered(bundle, it) }.removeSubPackages()

  val searchScope = bundle.getSearchScope(project)
  val psiClasses = readAction { javaSuites.flatMap { it.getCurrentSuiteClasses(project) }.distinct() }
  val classPackageEntries = readAction { psiClasses.mapNotNull { it.qualifiedName } }
    .filter { className -> packageNames.none { className.isInPackage(it) } }
    .groupBy { StringUtil.getPackageName(it) }
    .map { (packageName, names) -> PackageEntry(packageName, names.map { StringUtil.getShortName(it) }) }

  val packageEntries = packageNames.map { PackageEntry(it, null) } + classPackageEntries

  val modules = readAction {
    if (packageNames.isNotEmpty()) {
      ModuleManager.getInstance(project).modules.toList()
    }
    else {
      psiClasses.mapNotNull { ModuleUtilCore.findModuleForPsiElement(it) }.distinct()
    }
  }.filter(searchScope::isSearchInModuleContent)

  return modules.flatMap { module ->
    CoverageOutputRoots.getRoots(coverageDataManager, module, bundle.isTrackTestFolders)
      .asSequence()
      .map(VirtualFile::toNioPath)
      .filter(::isValidOutputRoot)
      .distinct()
      .map { root -> ModuleRequest(module, root, packageEntries) }
      .toList()
  }
}

internal data class ModuleRequest(val module: Module, val root: Path, val packages: List<PackageEntry>)
internal data class PackageEntry(val packageName: String, val simpleClassNames: List<String>?)

@ApiStatus.Internal
object CoverageOutputRoots {
  @JvmStatic
  fun getRoots(manager: CoverageDataManager, module: Module, includeTests: Boolean): Array<VirtualFile> {
    val roots = manager.doInReadActionIfProjectOpen {
      var enumerator = OrderEnumerator.orderEntries(module).withoutSdk().withoutLibraries().withoutDepModules()
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
    if (packages.none { parent -> fqn.isInPackage(parent) }) {
      packages.add(fqn)
    }
  }
  return packages
}

private fun String.isInPackage(packageName: String): Boolean {
  return packageName.isEmpty() || this == packageName || this.startsWith("$packageName.")
}

private fun isValidOutputRoot(root: Path): Boolean {
  return Files.isDirectory(root) || Files.isRegularFile(root) && root.fileName.toString().endsWith(".jar", ignoreCase = true)
}

private fun isPackageFiltered(bundle: CoverageSuitesBundle, qualifiedName: String): Boolean {
  for (coverageSuite in bundle.suites) {
    if (coverageSuite is JavaCoverageSuite && coverageSuite.isPackageFiltered(qualifiedName)) {
      return true
    }
  }
  return false
}
