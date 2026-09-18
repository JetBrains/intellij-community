// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.eel

import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.inspections.eel.OptimizedEelFunctionCallNameProvider
import org.jetbrains.kotlin.psi.KtFile

private val OPTIMIZED_FUNCTION_CLASSES = setOf(
  "java.nio.file.Files",
  "com.intellij.openapi.util.io.NioFiles",
  "com.intellij.openapi.util.io.FileUtilRt",
)

internal class KtOptimizedEelFunctionCallNameProvider : OptimizedEelFunctionCallNameProvider {
  override fun getAliases(file: PsiFile, methodNames: Set<String>): Set<String> {
    if (file !is KtFile) return emptySet()

    return file.importDirectives.mapNotNullTo(HashSet()) { directive ->
      val importPath = directive.importPath ?: return@mapNotNullTo null
      if (importPath.fqName.parent().asString() !in OPTIMIZED_FUNCTION_CLASSES) return@mapNotNullTo null
      if (importPath.fqName.shortName().asString() !in methodNames) return@mapNotNullTo null
      directive.aliasName
    }
  }

}
