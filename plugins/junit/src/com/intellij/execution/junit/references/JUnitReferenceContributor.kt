// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.patterns.uast.capture
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import org.jetbrains.uast.*

class JUnitReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ),
      MethodSourceReference.Provider
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(
        ORG_JUNIT_JUPITER_CONDITION_PROVIDER_ENABLED_IF,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ),
      DisabledIfEnabledIfReference.Provider
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(
        ORG_JUNIT_JUPITER_CONDITION_PROVIDER_DISABLED_IF,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ),
      DisabledIfEnabledIfReference.Provider
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression()
        .annotationParam("names", capture(UAnnotation::class.java).filter { annotation ->
          val mode = annotation.findDeclaredAttributeValue("mode") ?: return@filter true // default is INCLUDE
          val name = ((mode as? UReferenceExpression)?.referenceNameElement as USimpleNameReferenceExpression?)?.identifier
          name == "INCLUDE" || name == "EXCLUDE"
        }),
      EnumSourceReference.Provider
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE, "resources"),
      object : UastInjectionHostReferenceProvider() {
        override fun getReferencesForInjectionHost(
          uExpression: UExpression,
          host: PsiLanguageInjectionHost,
          context: ProcessingContext
        ): Array<PsiReference> {
          @Suppress("UNCHECKED_CAST")
          return FileReferenceSet.createSet(host, false, false, false)
            .allReferences as Array<PsiReference>
        }
      }
    )
  }
}