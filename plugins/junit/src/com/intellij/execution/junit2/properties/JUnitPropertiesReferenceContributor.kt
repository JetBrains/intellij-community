// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.properties

import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

internal class JUnitPropertiesReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      psiElement(PropertyKeyImpl::class.java)
        .inFile(psiFile().withName(JUNIT_PLATFORM_PROPERTIES_CONFIG)),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          return arrayOf(JunitPropertyReference(element))
        }
      })
  }
}

private class JunitPropertyReference(element: PsiElement) : PsiReferenceBase<PsiElement>(element) {
  override fun resolve(): PsiElement? {
    val prop = element.parentOfType<Property>()
    val key = prop?.key

    return getJUnitPlatformProperties(element.containingFile)[key]?.declaration?.retrieve()
  }
}