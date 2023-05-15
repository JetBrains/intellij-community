// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.toml.getVersionCatalogParts
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue

class GradleKtsVersionCatalogReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val element = queryParameters.elementToSearch
        if (element !is TomlKeySegment) {
            return
        }

        val (keyValue, name) = runReadAction {
            element.parentOfType<TomlKeyValue>() to element.name
        }
        keyValue ?: return
        val nameParts = name?.getVersionCatalogParts() ?: return
        val identifier = nameParts.lastOrNull() ?: return
        val getter = PropertyUtilBase.getAccessorName(identifier, PropertyKind.GETTER)

        val searchScope = queryParameters.effectiveSearchScope/*.restrictToGradleKtsFiles()*/

        val searchContext = UsageSearchContext.IN_CODE
        val processor = MyProcessor(keyValue, nameParts)

        val search: (String) -> Unit = { queryParameters.optimizer.searchWord(it, searchScope, searchContext, true, element, processor) }
        search(identifier)
        search(getter)
    }

    class MyProcessor(private val searchedElement: TomlKeyValue, private val oldNameParts: List<String>) : RequestResultProcessor() {

        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            val parent = element.parentOfType<KtNameReferenceExpression>() ?: return true
            val handler = GradleKtsVersionCatalogGotoDeclarationHandler()
            val gotoDeclarationTargets = handler.getGotoDeclarationTargets(element, 0, null)
            if (gotoDeclarationTargets?.singleOrNull() == searchedElement) {
                return consumer.process(KtsVersionCatalogReference(parent, oldNameParts, searchedElement))
            }

            return true
        }
    }

    private class KtsVersionCatalogReference(
        refExpr: KtNameReferenceExpression,
        val oldNameParts: List<String>,
        val searchedElement: TomlKeyValue
    ) : PsiReferenceBase<KtNameReferenceExpression>(refExpr) {

        override fun resolve(): PsiElement {
            return searchedElement
        }
    }
}