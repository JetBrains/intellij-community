// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightClass
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField

/**
 * The results of GINQ execution reside in the `NamedRecord` class. This class does not provide any syntactic/bytecode info
 * about its runtime contents, so we should predict the "fields" by our own means. This is similar to map literals.
 */
internal class GrSyntheticNamedRecordClass(
  val typeParameters: List<PsiTypeParameter>,
  val typeMap: Map<String, Lazy<PsiType>>,
  val exposedBindings: List<String>, // bindings in `select` clause. Order is important
  private val namedRecord: PsiClass,
) : LightClass(namedRecord) {

  constructor(ginqExpression: GinqExpression, namedRecord: PsiClass) :
    this(emptyList(),
         getTypeMap(ginqExpression),
         ginqExpression.select?.projections?.mapNotNull { it.alias?.text } ?: emptyList(),
         namedRecord)

  private val pseudoFields: Lazy<List<GrField>> = lazyPub {
    typeMap.map { (name, ltype) ->
      GrLightField(this@GrSyntheticNamedRecordClass, name, ltype.value, navigationElement)
    }
  }

  override fun getFields(): Array<PsiField> {
    return super.getFields() + pseudoFields.value
  }

  override fun hasTypeParameters(): Boolean = typeParameters.isEmpty()

  override fun getTypeParameterList(): PsiTypeParameterList? {
    return null
  }

  override fun getTypeParameters(): Array<PsiTypeParameter> {
    return typeParameters.toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GrSyntheticNamedRecordClass) return false

    if (typeParameters != other.typeParameters) return false
    if (typeMap != other.typeMap) return false
    if (exposedBindings != other.exposedBindings) return false
    if (namedRecord != other.namedRecord) return false

    return true
  }

  override fun hashCode(): Int {
    var result = typeParameters.hashCode()
    result = 31 * result + typeMap.hashCode()
    result = 31 * result + exposedBindings.hashCode()
    result = 31 * result + namedRecord.hashCode()
    return result
  }
}

private fun getTypeMap(ginqExpression: GinqExpression): Map<String, Lazy<PsiType>> {
  val map = mutableMapOf<String, Lazy<PsiType>>()
  for (fragment in ginqExpression.getDataSourceFragments()) {
    val name = fragment.alias.referenceName ?: continue
    val type = lazyPub { inferDataSourceComponentType(fragment.dataSource.type) ?: PsiTypes.nullType() }
    map[name] = type
  }
  for (projection in ginqExpression.select?.projections ?: emptyList()) {
    // Actually, `projection.aggregatedExpression.text` is a valid key in the absence of `projection.alias`.
    // The problem is that the key in this case is a toString-ed parsed expression produced by the Groovy AST.
    // It is too hard to compute and maintain such representation in a groovy-compatible way, so we'd rather not support it at all.
    val name = projection.alias?.text ?: continue
    val type = lazyPub { projection.aggregatedExpression.type ?: PsiTypes.nullType() }
    map[name] = type
  }
  return map
}