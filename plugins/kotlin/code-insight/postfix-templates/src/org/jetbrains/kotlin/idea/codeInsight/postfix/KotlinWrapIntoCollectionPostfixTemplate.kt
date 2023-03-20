// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

internal class KotlinWrapIntoListPostfixTemplate(provider: KotlinPostfixTemplateProvider)
    : KotlinWrapIntoCollectionPostfixTemplate("listOf", provider)

internal class KotlinWrapIntoSetPostfixTemplate(provider: KotlinPostfixTemplateProvider)
    : KotlinWrapIntoCollectionPostfixTemplate("setOf", provider)

internal class KotlinWrapIntoSequencePostfixTemplate(provider: KotlinPostfixTemplateProvider)
    : KotlinWrapIntoCollectionPostfixTemplate("sequenceOf", provider)

internal abstract class KotlinWrapIntoCollectionPostfixTemplate : StringBasedPostfixTemplate {
    private val functionName: String

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(functionName: String, provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ functionName,
        /* example = */ "$functionName(expr)",
        /* selector = */ allExpressions(ValuedFilter),
        /* provider = */ provider
    ) {
        this.functionName = functionName
    }

    override fun getTemplateString(element: PsiElement) = "$functionName(\$expr$)\$END$"
    override fun getElementToRemove(expr: PsiElement) = expr
}