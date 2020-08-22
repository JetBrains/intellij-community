// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.paths.GlobalPathReferenceProvider.isWebReferenceUrl
import com.intellij.openapi.paths.UrlReference
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

internal class UrlReferenceProvider : PsiSymbolReferenceProvider {

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (!Registry.`is`("ide.symbol.url.references")) {
      return emptyList()
    }
    return CachedValuesManager.getCachedValue(element) {
      val configuration = IssueNavigationConfiguration.getInstance(element.project)
      val references = doGetReferences(element, configuration)
      CachedValueProvider.Result.create(references, element, configuration)
    }
  }

  private fun doGetReferences(host: PsiExternalReferenceHost, configuration: IssueNavigationConfiguration): Collection<PsiSymbolReference> {
    val commentText = StringUtil.newBombedCharSequence(host.text, 500)
    val linkMatches: List<IssueNavigationConfiguration.LinkMatch> = configuration.findIssueLinks(commentText)
    if (linkMatches.isEmpty()) {
      return emptyList()
    }
    val result = ArrayList<PsiSymbolReference>(linkMatches.size)
    for (linkMatch in linkMatches) {
      val url = linkMatch.targetUrl
      if (!isWebReferenceUrl(url)) continue
      result += UrlReference(host, linkMatch.range, url)
    }
    return result
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList() // doesn't support search
}
