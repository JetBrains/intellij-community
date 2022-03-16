// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD
import org.jetbrains.plugins.groovy.ext.ginq.ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.getClosestGinqTree
import org.jetbrains.plugins.groovy.ext.ginq.ast.ginqParents
import org.jetbrains.plugins.groovy.ext.ginq.resolve.resolveToCustomMember
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.lang.typing.box

fun inferDataSourceComponentType(type: PsiType?): PsiType? = when (type) {
  is PsiArrayType -> type.componentType
  is PsiClassType -> {
    extractComponent(type, CommonClassNames.JAVA_LANG_ITERABLE)
    ?: extractComponent(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM)
    ?: extractComponent(type, ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE)
  }
  else -> null
}

private fun extractComponent(type: PsiType, className: String): PsiType? {
  if (InheritanceUtil.isInheritor(type, className)) {
    return PsiUtil.substituteTypeParameter(type, className, 0, false) ?: PsiType.NULL
  }
  else {
    return null
  }
}

fun inferGeneralGinqType(macroCall: GrMethodCall, ginq: GinqExpression, psiGinq: GrExpression, isTop: Boolean): PsiType? {
  val invokedCall = macroCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName
  val containerCanonicalType =
    if (invokedCall == "GQL" && isTop) CommonClassNames.JAVA_UTIL_LIST
    else ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE

  val facade = JavaPsiFacade.getInstance(macroCall.project)
  val componentType = getComponentType(facade, psiGinq, ginq)
  return facade.findClass(containerCanonicalType, macroCall.resolveScope)?.let {
    facade.elementFactory.createType(it, componentType)
  }
}

private fun getComponentType(facade: JavaPsiFacade,
                             psiGinq: GrExpression,
                             ginq: GinqExpression): PsiType {
  val singleProjection = ginq.select.projections.singleOrNull()?.takeIf { it.alias == null }
  val projectionPsiType = if (singleProjection != null) {
    singleProjection.aggregatedExpression.type ?: PsiType.NULL
  }
  else {
    val namedRecord = facade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, psiGinq.resolveScope)
    namedRecord?.let { GrSyntheticNamedRecordClass(ginq, it).type() } ?: PsiType.NULL
  }
  return if (projectionPsiType == PsiType.NULL) PsiWildcardType.createUnbounded(psiGinq.manager) else projectionPsiType.box(psiGinq)
}

fun inferOverType(expression: GrMethodCall) : PsiType? {
  return expression.invokedExpression.castSafelyTo<GrReferenceExpression>()?.qualifierExpression?.type
}

fun inferLocalReferenceExpressionType(macroCall: GrMethodCall, refExpr: GrReferenceExpression): PsiType? {
  val tree = getClosestGinqTree(macroCall, refExpr) ?: return null
  if (refExpr.referenceName == "_g") {
    resolveToCustomMember(refExpr, "_g", tree)?.run { return type }
  }
  val resolved = refExpr.staticReference.resolve()
  val dataSourceFragment =
    refExpr.ginqParents(macroCall, tree).firstNotNullOfOrNull { parentGinq -> parentGinq.getDataSourceFragments().find { it.alias == resolved } }
  if (dataSourceFragment != null) {
    return inferDataSourceComponentType(dataSourceFragment.dataSource.type)
  }
  return null
}
