// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import java.util.Locale
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

object KDocTagCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        // findIdentifierPrefix() requires identifier part characters to be a superset of identifier start characters
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            StandardPatterns.character().javaIdentifierPart() or singleCharPattern('@'),
            StandardPatterns.character().javaIdentifierStart() or singleCharPattern('@')
        )

        if (parameters.isAutoPopup && prefix.isEmpty()) return
        if (prefix.isNotEmpty() && !prefix.startsWith('@')) {
            return
        }
        val kdocOwner = parameters.position.getNonStrictParentOfType<KDoc>()?.getOwner()
        val resultWithPrefix = result.withPrefixMatcher(prefix)
        KDocKnownTag.values().forEach {
            if (kdocOwner == null || it.isApplicable(kdocOwner)) {
                resultWithPrefix.addElement(LookupElementBuilder.create("@" + it.name.lowercase(Locale.US)))
            }
        }
    }

    private fun KDocKnownTag.isApplicable(declaration: KtDeclaration) = when (this) {
        KDocKnownTag.CONSTRUCTOR,
        KDocKnownTag.PROPERTY -> declaration is KtClassOrObject

        KDocKnownTag.RETURN -> declaration is KtNamedFunction

        KDocKnownTag.RECEIVER -> declaration is KtNamedFunction && declaration.receiverTypeReference != null

        KDocKnownTag.AUTHOR,
        KDocKnownTag.THROWS,
        KDocKnownTag.EXCEPTION,
        KDocKnownTag.PARAM,
        KDocKnownTag.SEE,
        KDocKnownTag.SINCE,
        KDocKnownTag.SAMPLE,
        KDocKnownTag.SUPPRESS -> true
    }
}
