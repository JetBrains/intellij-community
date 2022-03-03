// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.references

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.*
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import com.intellij.util.text.findTextRange
import org.intellij.plugins.intelliLang.inject.InjectLanguageAction
import org.intellij.plugins.intelliLang.inject.InjectorUtils

class LanguageReferenceContributor : PsiSymbolReferenceProvider {
  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    val psiComment = element.castSafelyTo<PsiComment>() ?: return emptyList()
    val languageRange = getLanguageRange(psiComment) ?: return emptyList()
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
    listOfNotNull(target.castSafelyTo<LanguageSymbol>()?.let { SearchRequest.of(it.name) })

}

private fun getLanguageRange(psiComment: PsiComment): TextRange? {
  val commentBody = psiComment.commentBody.trim()
  if (!commentBody.startsWith(LANGUAGE_PREFIX)) return null

  val startOffset = psiComment.text.findTextRange(LANGUAGE_PREFIX)!!.endOffset
  val endOffset = psiComment.text.findTextRange(commentBody)!!.endOffset
  return TextRange(startOffset, endOffset)
}

private data class LanguageSymbol(val name: String) : Symbol, Pointer<LanguageSymbol> {
  override fun createPointer(): Pointer<out Symbol> = this
  override fun dereference(): LanguageSymbol = this
}

class LanguageWordInCommentCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiComment(), object : CompletionProvider<CompletionParameters?>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (parameters.invocationCount < 2) return
        if (!Registry.`is`("org.intellij.intelliLang.comment.completion")) return
        val psiComment = parameters.originalPosition?.castSafelyTo<PsiComment>() ?: return
        val trimmedBody = psiComment.commentBody.trim()
        if (trimmedBody.isBlank() ||
            trimmedBody.length != LANGUAGE_PREFIX.length && LANGUAGE_PREFIX.startsWith(trimmedBody, true)) {
          result.addElement(LookupElementBuilder.create(LANGUAGE_PREFIX))
        }
      }
    })
  }
}

class LanguageCommentFolding : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    return SmartList<FoldingDescriptor>().also { result ->
      root.accept(object : PsiRecursiveElementVisitor() {
        override fun visitComment(comment: PsiComment) {
          super.visitComment(comment)
          if (getLanguageRange(comment) != null) {
            result.add(FoldingDescriptor(comment, comment.textRange))
          }
        }
      })
    }.toArray(FoldingDescriptor.EMPTY)
  }

  override fun getPlaceholderText(node: ASTNode): String? {
    val psiComment = node.psi.castSafelyTo<PsiComment>() ?: return null
    val languageRange = getLanguageRange(psiComment) ?: return null
    val writtenText = languageRange.substring(psiComment.text)
    return InjectorUtils.getLanguageByString(writtenText)?.id ?: writtenText
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true

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
  