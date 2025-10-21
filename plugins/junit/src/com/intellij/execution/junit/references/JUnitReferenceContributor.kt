// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.patterns.StandardPatterns.string
import com.intellij.patterns.uast.capture
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UReferenceExpression

class JUnitReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ),
      uastInjectionHostReferenceProvider { _, host -> arrayOf(MethodSourceReference(host)) }
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_FIELD_SOURCE,
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
      ),
      uastInjectionHostReferenceProvider { _, host -> arrayOf(FieldSourceReference(host)) }
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParams(
        listOf(ORG_JUNIT_JUPITER_CONDITION_PROVIDER_ENABLED_IF, ORG_JUNIT_JUPITER_CONDITION_PROVIDER_DISABLED_IF),
        string().equalTo(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
      ),
      uastInjectionHostReferenceProvider { _, host -> arrayOf(DisabledIfEnabledIfReference(host)) }
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression()
        .annotationParam("names", capture(UAnnotation::class.java).filter { annotation ->
          val mode = annotation.findDeclaredAttributeValue("mode")
                       as? UReferenceExpression
                     ?: return@filter true // default is INCLUDE
          val name = mode.resolvedName
          name == "INCLUDE" || name == "EXCLUDE"
        }),
      uastInjectionHostReferenceProvider { _, host -> arrayOf(EnumSourceReference(host)) }
    )
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().annotationParam(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE, "resources"),
      uastInjectionHostReferenceProvider { _, host ->
        @Suppress("UNCHECKED_CAST")
        FileReferenceSet.createSet(host, false, false, false)
          .allReferences as Array<PsiReference>
      }
    )
  }
}