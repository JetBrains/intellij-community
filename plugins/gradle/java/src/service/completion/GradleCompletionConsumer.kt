// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.properties.IProperty
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionsContributor
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField

class GradleCompletionConsumer(val delegate: GroovyCompletionConsumer) : GroovyCompletionConsumer by delegate {

  override fun consume(element: LookupElement) {
    val psi = element.psiElement ?: return delegate.consume(element)
    if (psi is GrLightField && psi.originInfo == GradleExtensionsContributor.propertiesFileOriginInfo) {
      val property = psi.navigationElement.castSafelyTo<IProperty>() ?: return delegate.consume(element)
      val value = property.value
      val newElement = LookupElementBuilder.createWithIcon(psi).withTailText("=$value").withTypeText(psi.type.presentableText, true)
      delegate.consume(newElement)
    } else {
      delegate.consume(element)
    }
  }

  override fun fastElementsProcessed(parameters: CompletionParameters) {
    GradleVersionCatalogCompletionContributor().fillCompletionVariants(parameters, delegate.completionResultSet)
    super.fastElementsProcessed(parameters)
  }
}