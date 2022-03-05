// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.types

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiType
import com.intellij.util.ProcessingContext
import com.intellij.util.castSafelyTo
import icons.JetgroovyIcons
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GrNamedRecordCompletionProvider : CompletionProvider<CompletionParameters>() {

  companion object {
    @JvmStatic
    fun register(contributor: CompletionContributor) {
      val provider = GrNamedRecordCompletionProvider()
      contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression::class.java)), provider)
    }
  }


  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val position = parameters.position.parent?.castSafelyTo<GrReferenceExpression>() ?: return
    val qualifier = position.qualifier ?: return
    val qualifierType = qualifier.type?.resolve()?.castSafelyTo<GrSyntheticNamedRecordClass>() ?: return
    for (entry in qualifierType.allKeys()) {
      var lookup: LookupElement = LookupElementBuilder.create(entry)
        .withTypeText(qualifierType[entry]?.takeIf { it != PsiType.NULL }?.presentableText)
        .withIcon(JetgroovyIcons.Groovy.Property)
        .withBoldness(true)
      lookup = PrioritizedLookupElement.withPriority(lookup, 1.0)
      result.addElement(lookup)
    }
  }
}