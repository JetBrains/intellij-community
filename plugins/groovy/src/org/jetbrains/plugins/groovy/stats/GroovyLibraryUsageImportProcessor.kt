// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.stats

import com.intellij.internal.statistic.libraryUsage.LibraryUsageImportProcessor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

internal class GroovyLibraryUsageImportProcessor : LibraryUsageImportProcessor<GrImportStatement> {
  override fun imports(file: PsiFile): List<GrImportStatement> {
    return file.asSafely<GroovyFile>()
      ?.importStatements
      ?.toList()
      .orEmpty()
  }

  override fun isSingleElementImport(import: GrImportStatement): Boolean = !import.isOnDemand

  override fun importQualifier(import: GrImportStatement): String? = import.importReference?.qualifiedReferenceName

  override fun resolve(import: GrImportStatement): PsiElement? = import.resolveTargetClass()
}