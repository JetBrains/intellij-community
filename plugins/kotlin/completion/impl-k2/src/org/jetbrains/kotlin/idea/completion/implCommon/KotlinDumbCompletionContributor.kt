// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PsiJavaPatterns.elementType
import com.intellij.patterns.PsiJavaPatterns.psiElement
import org.jetbrains.kotlin.idea.completion.KeywordCompletion
import org.jetbrains.kotlin.idea.completion.kotlinIdentifierPartPattern
import org.jetbrains.kotlin.idea.completion.kotlinIdentifierStartPattern
import org.jetbrains.kotlin.lexer.KtTokens

internal fun isDumbPsiCompletionEnabled(): Boolean {
    return Registry.`is`("kotlin.auto.completion.dumb.mode.use.psi.completion")
}

/**
 * A Kotlin code completion contributor that can run during indexing.
 * It does not use any indexes or makes any calls to resolve.
 * It only uses the PSI structure of the current file to generate completion results.
 * As such, the information returned is limited and can be wrong based on the context.
 */
class KotlinDumbCompletionContributor : CompletionContributor(), DumbAware {
    private val keywordCompletion = KeywordCompletion()

    private val psiTreeCompletion = PsiTreeCompletion()

    // Copied from the other completion contributors, we do not want to have completion after a number literal.
    private val AFTER_NUMBER_LITERAL = psiElement().afterLeafSkipping(
        psiElement().withText(""),
        psiElement().withElementType(elementType().oneOf(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL))
    )

    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet
    ) {
        if (!DumbService.isDumb(parameters.position.project) || !isDumbPsiCompletionEnabled()) {
            return
        }
        if (AFTER_NUMBER_LITERAL.accepts(parameters.position)) {
            result.stopHere()
            return
        }
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            kotlinIdentifierPartPattern(),
            kotlinIdentifierStartPattern()
        )
        val prefixMatcher = CamelHumpMatcher(prefix)

        // In dumb mode we do not know if the module is JVM or not, so in this case we just assume that it is.
        keywordCompletion.complete(parameters.position, prefixMatcher, isJvmModule = true) {
            result.addElement(it)
        }
        psiTreeCompletion.complete(parameters.position, prefixMatcher) {
            result.addElement(it)
        }
    }
}