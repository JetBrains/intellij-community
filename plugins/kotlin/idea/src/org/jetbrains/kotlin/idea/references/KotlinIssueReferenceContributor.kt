// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.SmartList
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinIssueReferenceContributor : PsiReferenceContributor() {
    object KotlinIssueReferenceProvider : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
            if (element !is KtStringTemplateExpression) return PsiReference.EMPTY_ARRAY

            val navigationConfiguration = IssueNavigationConfiguration.getInstance(element.project) ?: return PsiReference.EMPTY_ARRAY

            val refs = SmartList<PsiReference>()

            val issueText = StringUtil.newBombedCharSequence(element.text, 500)
            for (link in navigationConfiguration.findIssueLinks(issueText)) {
                val provider = PathReferenceManager.getInstance().globalWebPathReferenceProvider as GlobalPathReferenceProvider
                provider.createUrlReference(element, link.targetUrl, link.range, refs)
            }

            return refs.toArray(PsiReference.EMPTY_ARRAY)
        }

        // web references do not point to any real PsiElement
        override fun acceptsTarget(target: PsiElement): Boolean = false
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            KotlinIssueReferenceProvider,
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}