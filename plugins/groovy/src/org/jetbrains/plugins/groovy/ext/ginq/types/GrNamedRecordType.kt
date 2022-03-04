// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLiteralClassType

class GrNamedRecordType(private val expr: GinqExpression, languageLevel: LanguageLevel = LanguageLevel.JDK_1_5)
  : GrLiteralClassType(languageLevel, expr.from.fromKw.resolveScope, JavaPsiFacade.getInstance(expr.from.fromKw.project)) {
  companion object {
    const val ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD = "org.apache.groovy.ginq.provider.collection.runtime.NamedRecord"
  }

  private val mySyntheticClass: Lazy<GrSyntheticNamedRecordClass?> = lazyPub {
    val clazz = myFacade.findClass(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD, expr.from.fromKw.resolveScope)
    clazz?.let { GrSyntheticNamedRecordClass(expr, it) }
  }

  fun getGinqExpression(): GinqExpression = expr

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
    val superResult = super.resolveGenerics()
    return object : ClassResolveResult by superResult {
      override fun getElement(): GrSyntheticNamedRecordClass? = mySyntheticClass.value
    }
  }

  override fun resolve(): GrSyntheticNamedRecordClass? {
    return mySyntheticClass.value
  }

  override fun getJavaClassName(): String = ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_NAMED_RECORD
}