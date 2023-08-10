// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.properties

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope.moduleRuntimeScope
import com.intellij.psi.search.ProjectScope.getLibrariesScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager

internal const val JUNIT_PLATFORM_PROPERTIES_CONFIG: String = "junit-platform.properties"
internal const val JUNIT_CONSTANTS_CLASS: String = "org.junit.jupiter.engine.Constants"

internal const val JUNIT_PROPERTY_NAME_SUFFIX: String = "_PROPERTY_NAME"
internal const val JUNIT_DEFAULT_PARALLEL_EXECUTION_MODE_FIELD: String = "DEFAULT_PARALLEL_EXECUTION_MODE"

internal data class JUnitPlatformProperty(
  val key: String,
  val declaration: PsiAnchor
)

internal fun getJUnitPlatformProperties(file: PsiFile): Map<String, JUnitPlatformProperty> {
  return CachedValuesManager.getManager(file.project).getCachedValue(file, CachedValueProvider {
    val module = ModuleUtilCore.findModuleForFile(file)

    val javaPsi = JavaPsiFacade.getInstance(file.project)
    val constantsClass = module?.let { javaPsi.findClass(JUNIT_CONSTANTS_CLASS, moduleRuntimeScope(it, false)) }
                         ?: javaPsi.findClass(JUNIT_CONSTANTS_CLASS, getLibrariesScope(file.project))

    val psiEvaluator = PsiExpressionEvaluator()
    val properties = (constantsClass?.fields ?: emptyArray())
      .filter { it.name.endsWith(JUNIT_PROPERTY_NAME_SUFFIX) || it.name == JUNIT_DEFAULT_PARALLEL_EXECUTION_MODE_FIELD }
      .mapNotNull { field ->
        computePropertyName(psiEvaluator, field.initializer)
          ?.let { key -> JUnitPlatformProperty(key, PsiAnchor.create(field)) }
      }

    Result.create(properties.associateBy { it.key },
                  JavaLibraryModificationTracker.getInstance(file.project))
  })
}

internal fun computePropertyName(psiEvaluator: PsiExpressionEvaluator, nameInitializer: PsiExpression?): String? {
  if (nameInitializer == null) return null

  return psiEvaluator.computeConstantExpression(nameInitializer, true) as? String
}
