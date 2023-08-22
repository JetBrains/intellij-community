// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.references

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinIssueReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.provider,
            PsiReferenceRegistrar.LOWER_PRIORITY,
        )
    }
}