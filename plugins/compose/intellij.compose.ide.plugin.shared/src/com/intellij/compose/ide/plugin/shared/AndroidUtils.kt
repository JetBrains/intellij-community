// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile

private const val ANDROID_FACET_CLASS_NAME: String = "org.jetbrains.android.facet.AndroidFacet"

internal fun isAndroidModule(module: Module): Boolean {
  val facets = FacetManager.getInstance(module).allFacets
  return facets.any { it::class.java.name == ANDROID_FACET_CLASS_NAME }
}

internal fun isAndroidFile(psiFile: PsiFile): Boolean {
  return ModuleUtilCore.findModuleForFile(psiFile)?.let { isAndroidModule(it) } == true
}
