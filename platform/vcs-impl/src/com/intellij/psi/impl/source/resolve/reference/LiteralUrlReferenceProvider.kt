// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiLiteralValue

internal class LiteralUrlReferenceProvider : PsiSymbolReferenceProvider {

  private val urlReferenceProvider = UrlReferenceProvider()

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()

  override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
    if (element is PsiLiteralValue && element.value is String) {
      return urlReferenceProvider.getReferences(element, hints)
    }
    else {
      return emptyList()
    }
  }
}
