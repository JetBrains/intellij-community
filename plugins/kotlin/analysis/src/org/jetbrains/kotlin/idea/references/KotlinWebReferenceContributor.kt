// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.WebReference
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class KotlinWebReferenceContributor : PsiReferenceContributor() {
    private val spaceSymbolsSplitter: Regex = Regex("\\s")

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // see org.jetbrains.kotlin.idea.references.KtIdeReferenceProviderService.getReferences
        // ContributedReferenceHost elements are not queried for Kotlin-specific references, contribute using PsiReferenceRegistrar

        registrar.registerReferenceProvider(
            psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun acceptsTarget(target: PsiElement): Boolean {
                    return false // web references do not point to any real PsiElement
                }

                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val stringTemplateExpression = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
                    if (!stringTemplateExpression.textContains(':')) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val results = SmartList<PsiReference>()
                    for (entry in stringTemplateExpression.entries) {
                        if (entry.expression != null) continue

                        val texts = entry.text.split(spaceSymbolsSplitter)
                        var startIndex = entry.startOffsetInParent
                        for (text in texts) {
                            if (text.isNotEmpty() && GlobalPathReferenceProvider.isWebReferenceUrl(text)) {
                                results.add(WebReference(stringTemplateExpression, TextRange(startIndex, startIndex + text.length), text))
                            }
                            startIndex += text.length + 1
                        }
                    }

                    return results.toArray(PsiReference.EMPTY_ARRAY)
                }
            })
    }
}