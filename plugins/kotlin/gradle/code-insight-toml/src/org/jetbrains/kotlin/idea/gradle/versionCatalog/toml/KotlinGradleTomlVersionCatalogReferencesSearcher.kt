// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext.IN_CODE
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.plugins.gradle.toml.getTomlParentSectionName
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue

class KotlinGradleTomlVersionCatalogReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(false) {

    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val element = queryParameters.elementToSearch
        if (element !is TomlKeySegment) return
        val (tomlKeyValue, name) = runReadAction {
            element.parentOfType<TomlKeyValue>() to element.name
        }
        tomlKeyValue ?: return
        val nameParts = name?.getVersionCatalogParts() ?: return
        val identifier = nameParts.joinToString(".")
        val searchScope = queryParameters.effectiveSearchScope.restrictToKotlinSources()
        queryParameters.optimizer.searchWord(identifier, searchScope, IN_CODE, true, element, MyProcessor(tomlKeyValue))
    }

    class MyProcessor(private val declarationElement: TomlKeyValue) : RequestResultProcessor() {

        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            if (element !is KtDotQualifiedExpression || element.hasWrappingVersionCatalogExpression()) {
                return true
            }
            val handler = KotlinGradleTomlVersionCatalogGotoDeclarationHandler()
            // The handler doesn't work with KtDotQualifiedExpression directly, it expects its grandchild (LeafPsiElement)
            val grandChild = element.lastChild?.lastChild as? LeafPsiElement ?: return true
            val foundTargets = handler.getGotoDeclarationTargets(grandChild, 0, null)
            if (foundTargets?.singleOrNull() == declarationElement) {
                return consumer.process(KotlinVersionCatalogReference(element, declarationElement))
            }
            return true
        }
    }

    private class KotlinVersionCatalogReference(
        refExpr: KtDotQualifiedExpression,
        val searchedElement: TomlKeyValue
    ) : PsiReferenceBase<KtDotQualifiedExpression>(refExpr) {

        override fun resolve(): PsiElement {
            return searchedElement
        }

        override fun handleElementRename(newElementName: String): PsiElement {
            val newElementParts = newElementName.getVersionCatalogParts()
            val versionCatalogName = element.text.substringBefore(".")

            val section = getTomlParentSectionName(searchedElement) // versions, bundles, plugins, libraries
            val sectionPart = if (section == null || section == "libraries") "" else ".$section"

            val newElementText = versionCatalogName + sectionPart + newElementParts.joinToString(".", ".")
            val newElement = KtPsiFactory(element.project).createExpression(newElementText)
            return element.replace(newElement)
        }
    }

}

private fun String.getVersionCatalogParts(): List<String> = split("_", "-")