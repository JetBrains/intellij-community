// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.requests.generateCreateMethodActions
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.GrCreateClassKind
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
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
      if (GroovyConfigUtils.isAtLeastGroovy40(ref)) {
        result += factory.createClassFixAction(ref, GrCreateClassKind.RECORD)
      }
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

fun generateAddImportActions(ref: GrReferenceElement<*>): Collection<IntentionAction> {
  return generateAddImportAction(ref)?.let(::listOf) ?: emptyList()
}

private fun generateAddImportAction(ref: GrReferenceElement<*>): IntentionAction? {
  if (ref.isQualified) return null
  val referenceName = ref.referenceName ?: return null
  if (referenceName.isEmpty()) return null
  if (ref !is GrCodeReferenceElement && Character.isLowerCase(referenceName[0])) return null
  return GroovyQuickFixFactory.getInstance().createGroovyAddImportAction(ref)
}

fun generateReferenceExpressionFixes(ref: GrReferenceExpression): Collection<IntentionAction> {
  val targetClass = QuickfixUtil.findTargetClass(ref) ?: return emptyList()
  val factory = GroovyQuickFixFactory.getInstance()
  val actions = ArrayList<IntentionAction>()

  generateAddDynamicMemberAction(ref)?.let(actions::add)

  if (targetClass !is SyntheticElement || targetClass is GroovyScriptClass) {
    val parent = ref.parent
    if (parent is GrMethodCall) {
      actions += generateCreateMethodActions(parent)
    } else {
      actions += factory.createCreateFieldFromUsageFix(ref)
      if (PsiUtil.isAccessedForReading(ref)) {
        actions += factory.createCreateGetterFromUsageFix(ref, targetClass)
      }
      if (PsiUtil.isLValue(ref)) {
        actions += factory.createCreateSetterFromUsageFix(ref)
      }
    }
  }

  if (!ref.isQualified) {
    val owner = ref.parentOfType<GrVariableDeclarationOwner>()
    if (owner !is GroovyFileBase || owner.isScript) {
      actions += factory.createCreateLocalVariableFromUsageFix(ref, owner)
    }
    if (PsiTreeUtil.getParentOfType(ref, GrMethod::class.java) != null) {
      actions += factory.createCreateParameterFromUsageFix(ref)
    }
  }

  return actions
}

private fun generateAddDynamicMemberAction(referenceExpression: GrReferenceExpression): IntentionAction? {
  if (referenceExpression.containingFile?.virtualFile == null) {
    return null
  }
  if (PsiUtil.isCall(referenceExpression)) {
    val argumentTypes = PsiUtil.getArgumentTypes(referenceExpression, false) ?: return null
    return GroovyQuickFixFactory.getInstance().createDynamicMethodFix(referenceExpression, argumentTypes)
  }
  else {
    return GroovyQuickFixFactory.getInstance().createDynamicPropertyFix(referenceExpression)
  }
}
