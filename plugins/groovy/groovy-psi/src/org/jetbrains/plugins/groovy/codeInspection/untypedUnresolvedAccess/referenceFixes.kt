// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.canBeClassOrPackage

fun generateCreateClassActions(ref: GrReferenceElement<*>): Collection<IntentionAction> {
  if (ref.parentOfType<GrPackageDefinition>() != null) {
    return emptyList()
  }

  val factory = GroovyQuickFixFactory.getInstance()

  val parent = ref.parent
  if (parent is GrNewExpression && parent.referenceElement === ref) {
    return listOf(factory.createClassFromNewAction(parent))
  }

  if (ref is GrReferenceExpression && !canBeClassOrPackage(ref)) {
    return emptyList()
  }

  return when {
    classExpected(parent) -> listOf(
      factory.createClassFixAction(ref, GrCreateClassKind.CLASS),
      factory.createClassFixAction(ref, GrCreateClassKind.ENUM)
    )
    interfaceExpected(parent) -> listOf(
      factory.createClassFixAction(ref, GrCreateClassKind.INTERFACE),
      factory.createClassFixAction(ref, GrCreateClassKind.TRAIT)
    )
    annotationExpected(parent) -> listOf(
      factory.createClassFixAction(ref, GrCreateClassKind.ANNOTATION)
    )
    else -> {
      val result = mutableListOf(
        factory.createClassFixAction(ref, GrCreateClassKind.CLASS),
        factory.createClassFixAction(ref, GrCreateClassKind.INTERFACE)
      )
      if (!ref.isQualified || resolvesToGroovy(ref.qualifier)) {
        result += factory.createClassFixAction(ref, GrCreateClassKind.TRAIT)
      }
      result += factory.createClassFixAction(ref, GrCreateClassKind.ENUM)
      result += factory.createClassFixAction(ref, GrCreateClassKind.ANNOTATION)
      result
    }
  }
}

private fun classExpected(parent: PsiElement?): Boolean {
  return parent is GrExtendsClause && parent.parent !is GrInterfaceDefinition
}

private fun interfaceExpected(parent: PsiElement?): Boolean {
  return parent is GrImplementsClause || parent is GrExtendsClause && parent.parent is GrInterfaceDefinition
}

private fun annotationExpected(parent: PsiElement?): Boolean {
  return parent is GrAnnotation
}

private fun resolvesToGroovy(qualifier: PsiElement?): Boolean {
  return when (qualifier) {
    is GrReferenceElement<*> -> qualifier.resolve() is GroovyPsiElement
    is GrExpression -> {
      val type = qualifier.type as? PsiClassType
      type?.resolve() is GroovyPsiElement
    }
    else -> false
  }
}
