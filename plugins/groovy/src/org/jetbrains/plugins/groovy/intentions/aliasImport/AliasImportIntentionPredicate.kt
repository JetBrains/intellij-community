// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.aliasImport

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

object AliasImportIntentionPredicate : PsiElementPredicate {

  override fun satisfiedBy(element: PsiElement): Boolean {
    when (element) {
      is GrReferenceExpression -> {
        val result = element.advancedResolve()
        val context = result.currentFileResolveContext as? GrImportStatement ?: return false
        return context.isStatic && !context.isAliasedImport
      }
      is GrImportStatement -> {
        if (!element.isStatic || element.isAliasedImport || element.isOnDemand) return false
        val reference = element.importReference ?: return false
        return reference.resolve() is PsiMember
      }
      else -> return false
    }
  }
}
