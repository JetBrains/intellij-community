// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.impl.light.LightClass
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

/**
 * The results of GINQ execution reside in the `NamedRecord` class. This class does not provide any syntactic/bytecode info
 * about its runtime contents, so we should predict the "fields" by our own means. This is similar to map literals.
 */
class GrSyntheticNamedRecordClass(val typeParameters: List<PsiTypeParameter>,
                                  val typeMap: Map<String, Lazy<PsiType>>,
                                  private val namedRecord: PsiClass) : LightClass(namedRecord) {

  constructor(ginqExpression: GinqExpression, namedRecord: PsiClass) : this(emptyList(), getTypeMap(ginqExpression), namedRecord)

  operator fun get(name: String): PsiType? {
    return typeMap[name]?.value
  }

  fun allKeys(): Set<String> {
    return typeMap.keys
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

    if (namedRecord != other.namedRecord) return false
    if (typeMap != other.typeMap) return false

    return true
  }

  override fun hashCode(): Int {
    var result = namedRecord.hashCode()
    result = 31 * result + typeMap.hashCode()
    return result
  }
}

private fun getTypeMap(ginqExpression: GinqExpression): Map<String, Lazy<PsiType>> {
  val map = mutableMapOf<String, Lazy<PsiType>>()
  for (fragment in ginqExpression.getDataSourceFragments()) {
    val name = fragment.alias.referenceName ?: continue
    val type = lazyPub { fragment.dataSource.type?.let(::inferDataSourceComponentType) ?: GrLiteralClassType.NULL }
    map[name] = type
  }
  for (projection in ginqExpression.select.projections) {
    val name = projection.alias?.text ?: continue // todo expression name
    val type = lazyPub { projection.aggregatedExpression.type ?: GrLiteralClassType.NULL }
    map[name] = type
  }
  return map
}