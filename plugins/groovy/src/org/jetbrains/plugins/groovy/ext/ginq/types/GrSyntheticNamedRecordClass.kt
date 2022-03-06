// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

/**
 * Some usages of a PsiType involve re-creating it with some psi factory. The result is a PsiImmediateClassType.
 * It means that we cannot store information about fields in an inheritor of PsiType.
 * Instead we use a "custom" PsiClass
 */
class GrSyntheticNamedRecordClass(val typeParameters: List<PsiTypeParameter>, val typeMap: Map<String, Lazy<PsiType>>, private val namedRecord: PsiClass) : PsiClass by namedRecord, UserDataHolderBase() {

  constructor(ginqExpression: GinqExpression, namedRecord: PsiClass) : this(emptyList(), getTypeMap(ginqExpression), namedRecord)

  override fun isValid(): Boolean {
    return namedRecord.isValid
  }

  operator fun get(name: String): PsiType? {
    return typeMap[name]?.value
  }

  fun allKeys() : Set<String> {
    return typeMap.keys
  }

  override fun getSourceElement(): PsiElement? {
    return namedRecord.sourceElement
  }

  override fun hasTypeParameters(): Boolean = typeParameters.isEmpty()

  override fun getTypeParameterList(): PsiTypeParameterList? {
    return null
  }

  override fun getTypeParameters(): Array<PsiTypeParameter> {
    return typeParameters.toTypedArray()
  }

  override fun <T : Any?> getCopyableUserData(key: Key<T>): T? = namedRecord.getCopyableUserData(key)

  override fun <T : Any?> getUserData(key: Key<T>): T? = namedRecord.getUserData(key)

  override fun <T : Any?> putCopyableUserData(key: Key<T>, value: T?) : Unit = namedRecord.putCopyableUserData(key, value)

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) : Unit = namedRecord.putUserData(key, value)

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

private fun getTypeMap(ginqExpression: GinqExpression) : Map<String, Lazy<PsiType>> {
  val map = mutableMapOf<String, Lazy<PsiType>>()
  for (fragment in ginqExpression.getDataSourceFragments()) {
    val name = fragment.alias.referenceName ?: continue
    val type = lazyPub { fragment.dataSource.type?.let(::inferDataSourceComponentType) ?: GrLiteralClassType.NULL }
    map[name] = type
  }
  for (projection in ginqExpression.select.projections) {
    val name = projection.alias?.text ?: continue // todo
    val type = lazyPub { projection.aggregatedExpression.type ?: GrLiteralClassType.NULL }
    map[name] = type
  }
  return map
}