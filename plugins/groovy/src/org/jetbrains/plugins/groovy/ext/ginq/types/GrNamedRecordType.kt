// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.inferDataSourceComponentType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

class GrNamedRecordType(private val expr: GinqExpression, languageLevel: LanguageLevel = LanguageLevel.JDK_1_5)
  : GrLiteralClassType(languageLevel, expr.from.fromKw.resolveScope, JavaPsiFacade.getInstance(expr.from.fromKw.project)) {
  companion object {
    const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD = "org.apache.groovy.ginq.provider.collection.runtime.NamedRecord"
  }

  private val typeMap : Lazy<Map<String, Lazy<PsiType>>> = lazyPub {
    val map = mutableMapOf<String, Lazy<PsiType>>()
    for (fragment in expr.getDataSourceFragments()) {
      val name = fragment.alias.referenceName ?: continue
      val type = lazyPub { fragment.dataSource.type?.let(::inferDataSourceComponentType) ?: NULL }
      map[name] = type
    }
    map
  }

  operator fun get(name: String) : PsiType? {
    return typeMap.value[name]?.value
  }

  fun getGinqExpression() : GinqExpression = expr

  override fun isValid(): Boolean {
    return expr.from.fromKw.isValid
  }

  override fun getParameters(): Array<PsiType> {
    return emptyArray()
  }

  override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType {
    return GrNamedRecordType(expr, languageLevel)
  }

  override fun resolveGenerics(): ClassResolveResult {
    // a hack to avoid re-creation of NamedRecordType in PsiSubstitutorImpl#visitClassType. This re-creation will lead to the loss of field info
    return ClassResolveResult.EMPTY
  }

  override fun getJavaClassName(): String = ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD
}