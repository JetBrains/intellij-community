// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.text.VersionComparatorUtil
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import org.jetbrains.uast.UElement

internal fun isJUnit3InScope(file: PsiFile): Boolean {
  return hasInModuleScope(file, JUNIT_FRAMEWORK_TEST_CASE)
}

internal fun isJUnit4InScope(file: PsiFile): Boolean {
  return hasInModuleScope(file, ORG_JUNIT_TEST)
}

internal fun isJUnit5InScope(file: PsiFile): Boolean {
  return hasInModuleScope(file, ORG_JUNIT_JUPITER_API_TEST)
}

private fun hasInModuleScope(file: PsiFile, detectionClass: String): Boolean {
  val vFile = file.originalFile.virtualFile ?: return false
  val module = ModuleUtil.findModuleForFile(file) ?: return false
  val productionScope = module.getModuleScope(false)
  if (!productionScope.contains(vFile)) {
    return JavaLibraryUtil.hasLibraryClass(module, detectionClass)
  }

  return getProductionClassDetectionMap(module).getOrDefault(detectionClass, false)
}

private fun getProductionClassDetectionMap(module: Module): Map<String, Boolean> {
  return CachedValuesManager.getManager(module.project).getCachedValue(module, CachedValueProvider {
    val map = ConcurrentFactoryMap.createMap<String, Boolean> {
      val productionScope = module.getModuleWithDependenciesAndLibrariesScope(false)
      JavaPsiFacade.getInstance(module.project).findClass(it, productionScope) != null
    }
    Result.create(map, JavaLibraryModificationTracker.getInstance(module.project))
  })
}

class JUnitVersion(val asString: String) : Comparable<JUnitVersion> {
  override fun compareTo(other: JUnitVersion): Int {
    return VersionComparatorUtil.compare(asString, other.asString)
  }

  companion object {
    val V_3_X = JUnitVersion("3")
    val V_4_X = JUnitVersion("4")
    val V_5_X = JUnitVersion("5")
    val V_5_8_0 = JUnitVersion("5.8.0")
    val V_5_10_0 = JUnitVersion("5.10.0")
  }
}

private const val JUNIT_3_AND_4_COORDINATES = "junit:junit"

private const val JUNIT_5_COORDINATES = "org.junit.jupiter:junit-jupiter-api"

internal fun getUJUnitVersion(elem: UElement): JUnitVersion? {
  val sourcePsi = elem.sourcePsi ?: return null
  return getJUnitVersion(sourcePsi)
}

internal fun getJUnitVersion(elem: PsiElement): JUnitVersion? {
  val module = ModuleUtil.findModuleForPsiElement(elem) ?: return null
  return getJUnitVersion(module)
}

/**
 * Gets latest available JUnit version in the class path.
 */
internal fun getJUnitVersion(module: Module): JUnitVersion? {
  if (module.isDisposed() || module.getProject().isDefault) return null
  val junit5Version = JavaLibraryUtil.getLibraryVersion(module, JUNIT_5_COORDINATES)?.substringBeforeLast("-")
  if (junit5Version != null) return JUnitVersion(junit5Version)
  val junit3Or4Version = JavaLibraryUtil.getLibraryVersion(module, JUNIT_3_AND_4_COORDINATES)?.substringBeforeLast("-")
  if (junit3Or4Version != null) return JUnitVersion(junit3Or4Version)
  return null
}