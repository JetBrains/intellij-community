// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.references

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.LanguageCommenters
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.util.castSafelyTo
import com.intellij.util.text.findTextRange
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction

class LanguageReferenceContributor : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val psiComment = element.castSafelyTo<PsiComment>() ?: return emptyList()
    val commentBody = psiComment.commentBody.trim()
    if (!commentBody.startsWith(LANGUAGE_PREFIX)) return emptyList()

    val startOffset = psiComment.text.findTextRange(LANGUAGE_PREFIX)!!.endOffset
    val endOffset = psiComment.text.findTextRange(commentBody)!!.endOffset
    return listOf(object : PsiCompletableReference {

      override fun getCompletionVariants(): Collection<LookupElement> =
        InjectLanguageAction.getAllInjectables().map {
          LookupElementBuilder.create(it.id.lowercase()).withIcon(it.icon).withTailText("(${it.displayName})", true)
        }

      override fun getElement(): PsiElement = element

      override fun getRangeInElement(): TextRange = TextRange(startOffset, endOffset)

      override fun resolveReference(): Collection<Symbol> = listOf(LanguageSymbol(rangeInElement.substring(this.element.text)))
    })
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> =
    listOfNotNull(target.castSafelyTo<LanguageSymbol>()?.let { SearchRequest.of(it.name) })

}

private data class LanguageSymbol(val name: String) : Symbol, Pointer<LanguageSymbol> {
  override fun createPointer(): Pointer<out Symbol> = this
  override fun dereference(): LanguageSymbol = this

}

private const val LANGUAGE_PREFIX = "language="

@Suppress("NAME_SHADOWING")
private val PsiComment.commentBody: String
  get() {
    val text = text
    val commenter = LanguageCommenters.INSTANCE.forLanguage(language) ?: return text
    for (lineCommentPrefix in commenter.lineCommentPrefixes) {
      if (text.startsWith(lineCommentPrefix)) return text.substring(lineCommentPrefix.length)
    }
    return text.let { text -> commenter.blockCommentPrefix?.let { text.removePrefix(it) } ?: text }
      .let { text -> commenter.blockCommentSuffix?.let { text.removeSuffix(it) } ?: text }
  }
  