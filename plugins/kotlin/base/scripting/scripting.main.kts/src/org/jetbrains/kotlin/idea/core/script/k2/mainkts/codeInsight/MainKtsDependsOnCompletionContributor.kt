// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

@ApiStatus.Internal
class MainKtsDependsOnCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, DEPENDS_ON_PATTERN, MainKtsDependsOnCompletionProvider())
    }
}

internal val DEPENDS_ON_PATTERN = psiElement(LeafPsiElement::class.java)
    .withSuperParent(1, KtLiteralStringTemplateEntry::class.java)
    .withSuperParent(2, KtStringTemplateExpression::class.java)
    .withSuperParent(3, KtValueArgument::class.java)
    .withSuperParent(4, psiElement(KtValueArgumentList::class.java))
    .withSuperParent(5, psiElement(KtAnnotationEntry::class.java).with(
        object : PatternCondition<KtAnnotationEntry>("isDependsOnAnnotation") {
            private val shortName = DEPENDS_ON_FQN.shortName()
            private val fullName = DEPENDS_ON_FQN.asString()
            override fun accepts(ann: KtAnnotationEntry, context: ProcessingContext?): Boolean {
                return ann.shortName == shortName &&
                        (ann.calleeExpression?.text == shortName.asString() || ann.calleeExpression?.text == fullName)
            }
        }
    ))
    .inFile(psiFile(PsiFile::class.java).with(
        object : PatternCondition<PsiFile>("isMainKtsScript") {
            override fun accepts(file: PsiFile, context: ProcessingContext?): Boolean {
                return file is KtFile && file.name.endsWith("main.kts") && file.isScript()
            }
        }
    ))
