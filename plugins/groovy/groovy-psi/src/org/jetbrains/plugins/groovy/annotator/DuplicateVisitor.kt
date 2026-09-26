// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter

class DuplicateVisitor: PsiRecursiveElementWalkingVisitor() {
  private val nameToElementMap : MutableMap<@NlsSafe String, MutableSet<PsiElement>> = mutableMapOf()

  override fun visitElement(element: PsiElement) {
    if (element is GrImportStatement) {
      visitImportStatement(element)
    }
    else if (element is GrTypeDefinition && element !is GrTypeParameter) {
      visitTypeDefinition(element)
    }

    super.visitElement(element)
  }
  
  private fun visitImportStatement(importStatement: GrImportStatement) {
    if (importStatement.isOnDemand) return
    val name = if (importStatement.isAliasedImport) {
      importStatement.alias?.name
    } else {
      importStatement.importedName
    } ?: return
    nameToElementMap.getOrPut(name) { mutableSetOf() }.add(importStatement)
  }

  private fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
    val name = typeDefinition.name ?: return
    nameToElementMap.getOrPut(name) { mutableSetOf() }.add(typeDefinition)
  }

  fun getDuplicateElements(): Map<@NlsSafe String, Set<PsiElement>> {
    return nameToElementMap.filterValues { it.size > 1 }
  }
}