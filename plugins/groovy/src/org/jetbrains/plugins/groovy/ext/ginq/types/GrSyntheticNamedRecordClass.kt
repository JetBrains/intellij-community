// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

/**
 * Some usages of a PsiType involve re-creating it with some psi factory. The result is a PsiImmediateClassType.
 * It means that we cannot store information about types in an inheritor of PsiType.
 * Instead we use a "custom" PsiClass
 */
class GrSyntheticNamedRecordClass(val expr: GinqExpression, private val namedRecord: PsiClass) : PsiClass by namedRecord {

  private val typeMap: Lazy<Map<String, Lazy<PsiType>>> = lazyPub {
    val map = mutableMapOf<String, Lazy<PsiType>>()
    for (fragment in expr.getDataSourceFragments()) {
      val name = fragment.alias.referenceName ?: continue
      val type = lazyPub { fragment.dataSource.type?.let(::inferDataSourceComponentType) ?: GrLiteralClassType.NULL }
      map[name] = type
    }
    for (projection in expr.select.projections) {
      val name = projection.alias?.text ?: projection.expression.text
      val type = lazyPub { projection.expression.type ?: GrLiteralClassType.NULL }
      map[name] = type
    }
    map
  }

  fun getGinqExpression() : GinqExpression {
    return expr
  }

  override fun isValid(): Boolean {
    return namedRecord.isValid && expr.isValid()
  }

  operator fun get(name: String): PsiType? {
    return typeMap.value[name]?.value
  }

  override fun getSourceElement(): PsiElement? {
    return namedRecord.getSourceElement()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GrSyntheticNamedRecordClass) return false

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    return expr.hashCode()
  }


}