// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Processor
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.IdeaPlugin

/**
 * Searches for [plugin alias][IdeaPlugin.getModules] references.
 */
internal class PluginAliasReferencesQueryExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val elementToSearch = queryParameters.elementToSearch
    if (elementToSearch !is XmlTag) return
    val ideaPlugin = DomUtil.getDomElement(elementToSearch) as? IdeaPlugin ?: return
    ideaPlugin.modules
      .mapNotNull { it.value.stringValue }
      .forEach { pluginAlias ->
        queryParameters.optimizer.searchWord(pluginAlias, queryParameters.effectiveSearchScope, true, elementToSearch)
      }
  }
}
