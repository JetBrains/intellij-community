// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.properties.IProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentsOfType
import com.intellij.ui.JBColor
import com.intellij.util.asSafely
import com.intellij.util.lazyPub
import org.jetbrains.plugins.gradle.codeInspection.groovy.DelegationHierarchy
import org.jetbrains.plugins.gradle.codeInspection.groovy.getDelegationHierarchy
import org.jetbrains.plugins.gradle.codeInspection.groovy.getDelegationSourceCaller
import org.jetbrains.plugins.gradle.service.resolve.GradleConfigureExtensionMethod
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionProperty
import org.jetbrains.plugins.gradle.service.resolve.GradlePropertyExtensionsContributor
import org.jetbrains.plugins.groovy.lang.completion.api.GroovyCompletionConsumer
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import kotlin.math.absoluteValue

class GradleCompletionConsumer(val position: PsiElement, val delegate: GroovyCompletionConsumer) : GroovyCompletionConsumer by delegate {

  private val delegationHierarchy: DelegationHierarchy by lazyPub {
    getDelegationHierarchy(position)
  }

  private val extensionElementHierarchy: GradleExtensionNameHierarchy by lazyPub {
    getGradleExtensionHierarchy(position)
  }

  override fun consume(element: LookupElement) {
    val psi = element.psiElement ?: return delegate.consume(element)
    val definingClass = getDelegationSourceCaller(delegationHierarchy, psi)
    if (definingClass != null && definingClass != delegationHierarchy.list.firstOrNull()?.first && psi is PsiNamedElement) {
      val newElement = element.modify { withItemTextForeground(JBColor.GRAY) }
      GradleLookupWeigher.setGradleCompletionPriority(newElement, GradleLookupWeigher.DEFAULT_COMPLETION_PRIORITY - 1)
      delegate.consume(newElement)
    }
    else if (psi is GrLightField && psi.originInfo == GradlePropertyExtensionsContributor.PROPERTIES_FILE_ORIGINAL_INFO) {
      val property = psi.navigationElement.asSafely<IProperty>() ?: return delegate.consume(element)
      val value = property.value
      val newElement = element.modify { withTailText("=$value").withTypeText(psi.type.presentableText, true) }
      delegate.consume(newElement)
    }
    else if (psi is GradleExtensionProperty) {
      val parentGradleExtensionNames = extensionElementHierarchy.names;
      val parentExtensionName = parentGradleExtensionNames.joinToString(".")

      if (parentExtensionName == psi.parentKey) {
        delegate.consume(element)
      }

      return
    }
    else if (psi is GradleConfigureExtensionMethod) {
      val parentGradleExtensionNames = extensionElementHierarchy.names;
      val parentExtensionName = parentGradleExtensionNames.joinToString(".")

      if (parentExtensionName == psi.parentKey) {
        delegate.consume(element)
      }

      return
    }
    else {
      delegate.consume(element)
    }
  }

  fun LookupElement.modify(modifier: LookupElementBuilder.() -> LookupElementBuilder): LookupElement {
    val lookupBuilder = `as`(LookupElementBuilder::class.java) ?: return fallback(modifier)
    val modified = lookupBuilder.modifier()
    val prioritized = `as`(PrioritizedLookupElement::class.java) ?: return modified
    if (prioritized.grouping != 0) {
      return PrioritizedLookupElement.withGrouping(modified, prioritized.grouping)
    }
    else if (prioritized.explicitProximity != 0) {
      return PrioritizedLookupElement.withExplicitProximity(modified, prioritized.grouping)
    }
    else if (prioritized.priority.absoluteValue > 1e-6) {
      return PrioritizedLookupElement.withPriority(modified, prioritized.priority)
    }
    return modified
  }

  private fun LookupElement.fallback(modifier: LookupElementBuilder.() -> LookupElementBuilder): LookupElement {
    val psi = psiElement.asSafely<PsiNamedElement>() ?: return this
    return LookupElementBuilder.createWithIcon(psi).modifier()
  }

  override fun fastElementsProcessed(parameters: CompletionParameters) {
    GradleVersionCatalogCompletionContributor().fillCompletionVariants(parameters, delegate.completionResultSet)
    super.fastElementsProcessed(parameters)
  }
}

fun getGradleExtensionHierarchy(place: PsiElement): GradleExtensionNameHierarchy {
  return GradleExtensionNameHierarchy(
    place.parentsOfType<GrFunctionalExpression>().map { it.parent }.filter { it is GrMethodCallExpression }
      .map { (it as GrMethodCallExpression).explicitCallReference }
      .map { it?.methodName?: "" }.toList()
  )
}

@JvmInline
value class GradleExtensionNameHierarchy(val names: List<String>)