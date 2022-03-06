// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GinqUtils")

package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil

const val GROOVY_GINQ_TRANSFORM_GQ = "groovy.ginq.transform.GQ"

val ginqMethods: Set<String> = setOf(
  "GQ",
  "GQL",
)

val joins : Set<String> = setOf(
  "join",
  "innerjoin",
  "innerhashjoin",
  "leftjoin",
  "lefthashjoin",
  "crossjoin",
  "rightjoin",
  "righthashjoin",
  "fulljoin",
  "fullhashjoin",
)

fun inferDataSourceComponentType(type: PsiType): PsiType? = when (type) {
  is PsiArrayType -> type.componentType
  is PsiClassType -> {
    extractComponent(type, CommonClassNames.JAVA_LANG_ITERABLE)
    ?: extractComponent(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM)
    ?: extractComponent(type, ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE)
  }
  else -> null
}

private fun extractComponent(type : PsiType, className: String) : PsiType? {
  if (InheritanceUtil.isInheritor(type, className)) {
    return PsiUtil.substituteTypeParameter(type, className, 0, false)
  } else {
    return null
  }
}

const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE : String =
  "org.apache.groovy.ginq.provider.collection.runtime.Queryable"
