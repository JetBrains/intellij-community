// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.references.LanguageInjectionUtil.commentBody
import org.intellij.plugins.intelliLang.references.LanguageInjectionUtil.languageRange

internal class LanguageReferenceContributor : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val psiComment = element.asSafely<PsiComment>() ?: return emptyList()
    val languageRange = languageRange(psiComment) ?: return emptyList()
    return listOf(object : PsiCompletableReference {

      override fun getCompletionVariants(): Collection<LookupElement> =
        InjectLanguageAction.getAllInjectables().map {
          LookupElementBuilder.create(it.id.lowercase()).withIcon(it.icon).withTailText("(${it.displayName})", true)
        }

      override fun getElement(): PsiElement = element

      override fun getRangeInElement(): TextRange = languageRange

      override fun resolveReference(): Collection<Symbol> = listOf(LanguageSymbol(rangeInElement.substring(this.element.text)))
    })
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> =
    listOfNotNull(target.asSafely<LanguageSymbol>()?.let { SearchRequest.of(it.name) })

}

private data class LanguageSymbol(val name: String) : Symbol, Pointer<LanguageSymbol> {
  override fun createPointer(): Pointer<out Symbol> = this
  override fun dereference(): LanguageSymbol = this
}

internal class LanguageWordInCommentCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiComment(), object : CompletionProvider<CompletionParameters?>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (parameters.invocationCount < 2) return
        if (!Registry.`is`("org.intellij.intelliLang.comment.completion")) return
        val psiComment = parameters.originalPosition?.asSafely<PsiComment>() ?: return
        val trimmedBody = commentBody(psiComment).trim()
        if (trimmedBody.isBlank() ||
            trimmedBody.length != LANGUAGE_PREFIX.length && LANGUAGE_PREFIX.startsWith(trimmedBody, true)) {
          result.addElement(LookupElementBuilder.create(LANGUAGE_PREFIX))
        }
      }
    })
  }
}
