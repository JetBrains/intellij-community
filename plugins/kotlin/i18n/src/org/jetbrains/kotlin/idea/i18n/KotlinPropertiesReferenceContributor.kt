// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.i18n

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPropertiesReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            KotlinPropertyKeyReferenceProvider
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            KotlinResourceBundleNameReferenceProvider
        )
    }
}