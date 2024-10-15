// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinIssueReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.provider

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            provider,
            PsiReferenceRegistrar.LOWER_PRIORITY,
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KDocSection::class.java),
            provider,
            PsiReferenceRegistrar.LOWER_PRIORITY,
        )
    }
}