// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parentsOfType
import com.intellij.ui.JBColor
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.headTail
import com.intellij.util.lazyPub
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionsContributor
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo
import kotlin.math.absoluteValue

class GradleCompletionConsumer(val position: PsiElement, val delegate: GroovyCompletionConsumer) : GroovyCompletionConsumer by delegate {

  private val delegationHierarchy: List<DelegatesToInfo?> by lazyPub {
    position.parentsOfType<GrFunctionalExpression>().map { getDelegatesToInfo(it) }.toList()
  }

  override fun consume(element: LookupElement) {
    val psi = element.psiElement ?: return delegate.consume(element)
    if (isForeignElement(psi) && psi is PsiNamedElement) {
      val newElement = element.modify { withItemTextForeground(JBColor.GRAY) }
      GradleLookupWeigher.setGradleCompletionPriority(newElement, GradleLookupWeigher.DEFAULT_COMPLETION_PRIORITY - 1)
      delegate.consume(newElement)
    } else if (psi is GrLightField && psi.originInfo == GradleExtensionsContributor.propertiesFileOriginInfo) {
      val property = psi.navigationElement.castSafelyTo<IProperty>() ?: return delegate.consume(element)
      val value = property.value
      val newElement = element.modify { withTailText("=$value").withTypeText(psi.type.presentableText, true) }
      delegate.consume(newElement)
    } else {
      delegate.consume(element)
    }
  }

  fun LookupElement.modify(modifier: LookupElementBuilder.() -> LookupElementBuilder) : LookupElement {
    val lookupBuilder = `as`(LookupElementBuilder::class.java) ?: return fallback(modifier)
    val modified = lookupBuilder.modifier()
    val prioritized = `as`(PrioritizedLookupElement::class.java) ?: return modified
    if (prioritized.grouping != 0) {
      return PrioritizedLookupElement.withGrouping(modified, prioritized.grouping)
    } else if (prioritized.explicitProximity != 0) {
      return PrioritizedLookupElement.withExplicitProximity(modified, prioritized.grouping)
    } else if (prioritized.priority.absoluteValue > 1e-6 ) {
      return PrioritizedLookupElement.withPriority(modified, prioritized.priority)
    }
    return modified
  }

  private fun LookupElement.fallback(modifier: LookupElementBuilder.() -> LookupElementBuilder): LookupElement {
    val psi = psiElement.castSafelyTo<PsiNamedElement>() ?: return this
    return LookupElementBuilder.createWithIcon(psi).modifier()
  }

  private fun isForeignElement(psiElement: PsiElement) : Boolean {
    val qualifiedName = psiElement.containingFile.castSafelyTo<PsiClassOwner>()?.classes?.singleOrNull()?.qualifiedName?.takeIf { it.startsWith("org.gradle") } ?: return false
    if (delegationHierarchy.size < 2) {
      return false
    }
    val (firstInfo, rest) = delegationHierarchy.headTail()
    if (firstInfo != null && retrievedFromDelegate(firstInfo, qualifiedName)) {
      return false
    }
    for (containingInfo in rest.filterNotNull()) {
      if (retrievedFromDelegate(containingInfo, qualifiedName)) {
        return true
      }
    }
    return false
  }

  private fun retrievedFromDelegate(first: DelegatesToInfo, name: @NlsSafe String) =
    first.admitsDelegate() && InheritanceUtil.isInheritor(first.typeToDelegate.resolve(), name)

  private fun DelegatesToInfo.admitsDelegate() : Boolean {
    return strategy != Closure.OWNER_ONLY
  }

  override fun fastElementsProcessed(parameters: CompletionParameters) {
    GradleVersionCatalogCompletionContributor().fillCompletionVariants(parameters, delegate.completionResultSet)
    super.fastElementsProcessed(parameters)
  }
}