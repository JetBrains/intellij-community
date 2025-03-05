// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.Processor
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.Action

/**
 * Search for implicit [Action] `id` if not specified.
 * Reference for [Action.getEffectiveId] to [Action.getClazz] (short class name).
 *
 * @see DevKitRelatedPropertiesProvider
 */
internal class ActionWithoutIdReferencesQueryExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val elementToSearch = queryParameters.elementToSearch

    if (elementToSearch !is XmlAttributeValue) return
    if (elementToSearch.hostName != "class") return

    val domElement = DomUtil.getDomElement(elementToSearch) ?: return
    val action = DomUtil.getParentOfType(domElement, Action::class.java, true) ?: return
    if (DomUtil.hasXml(action.id)) return

    val effectiveId = action.effectiveId ?: return
    queryParameters.optimizer.searchWord(effectiveId, queryParameters.effectiveSearchScope, true, elementToSearch)
  }
}