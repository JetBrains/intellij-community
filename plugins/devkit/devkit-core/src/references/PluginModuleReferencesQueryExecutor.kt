// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Processor
import com.intellij.util.xml.DomUtil
import org.jetbrains.idea.devkit.dom.IdeaPlugin
import org.jetbrains.idea.devkit.util.DescriptorUtil

/**
 * Searches for plugin module references.
 */
internal class PluginModuleReferencesQueryExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val elementToSearch = queryParameters.elementToSearch
    if (elementToSearch !is XmlTag) return
    if (DomUtil.getDomElement(elementToSearch) !is IdeaPlugin) return
    val moduleName = getModuleName(elementToSearch) ?: return
    queryParameters.optimizer.searchWord(moduleName, queryParameters.effectiveSearchScope, true, elementToSearch)
  }

  private fun getModuleName(elementToSearch: XmlTag): String? {
    val containingFile = elementToSearch.containingFile ?: return null
    if (!DescriptorUtil.isPluginModuleFile(containingFile)) return null
    return containingFile.containingFile.name.removeSuffix(".xml")
  }

}
