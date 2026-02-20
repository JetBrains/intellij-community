// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference

import com.intellij.ide.BrowserUtil
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.IssueDocumentationTargetProvider
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.references.PsiPolySymbolReferenceProvider
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainTextFile

internal class CommitMessageReferenceProvider : PsiPolySymbolReferenceProvider<PsiPlainTextFile> {

  override fun getOffsetsToReferencedSymbols(psiElement: PsiPlainTextFile): Map<Int, PolySymbol> {
    if (!CommitMessage.isCommitMessage(psiElement)) return emptyMap()
    val text = psiElement.text
    val bombedText = StringUtil.newBombedCharSequence(text, 500)
    val project = psiElement.project
    val configuration = IssueNavigationConfiguration.getInstance(project)
    val linkMatches: List<IssueNavigationConfiguration.LinkMatch> = configuration.findIssueLinks(bombedText)
    if (linkMatches.isEmpty()) {
      return emptyMap()
    }
    val result = mutableMapOf<Int, PolySymbol>()
    for (linkMatch in linkMatches) {
      if (linkMatch.isIssueMatch) {
        result[linkMatch.range.startOffset] = LazyDocumentationIssueSymbol(project, linkMatch.range.substring(text), linkMatch)
      }
    }
    return result
  }

  private class LazyDocumentationIssueSymbol(
    private val project: Project,
    override val name: @NlsSafe String,
    private val linkMatch: IssueNavigationConfiguration.LinkMatch,
  ) : PolySymbol {
    override val kind: PolySymbolKind = PolySymbolKind["vcs", "issue"]

    override fun createPointer(): Pointer<out PolySymbol> = hardPointer(this)

    override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
      IssueDocumentationTargetProvider.getIssueDocumentationTarget(project, name, linkMatch.targetUrl)

    override fun <T : Any> get(property: PolySymbolProperty<T>): T? =
      when (property) {
        PolySymbol.PROP_IJ_TEXT_ATTRIBUTES_KEY -> property.tryCast(EditorColors.REFERENCE_HYPERLINK_COLOR.externalName)
        else -> super.get(property)
      }

    override val presentation: TargetPresentation
      get() = TargetPresentation.builder(name).presentation()

    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
      listOf(
        object : NavigationTarget {
          override fun createPointer(): Pointer<NavigationTarget> = hardPointer(this)
          override fun computePresentation(): TargetPresentation = this@LazyDocumentationIssueSymbol.presentation
          override fun navigationRequest(): NavigationRequest? = object : Navigatable {
            override fun canNavigate(): Boolean = true
            override fun navigate(requestFocus: Boolean) = BrowserUtil.browse(linkMatch.targetUrl)
          }.navigationRequest()
        })
  }

}