// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class ConvertCollectionFix(
    element: KtExpression,
    val type: CollectionType
) : PsiUpdateModCommandAction<KtExpression>(element) {
    enum class CollectionType(
        val functionCall: String,
        val fqName: FqName,
        val literalFunctionName: String? = null,
        val emptyCollectionFunction: String? = null,
        private val nameOverride: String? = null
    ) {
        List("toList()", FqName("kotlin.collections.List"), "listOf", "emptyList"),
        Collection("toList()", FqName("kotlin.collections.Collection"), "listOf", "emptyList"),
        Iterable("toList()", FqName("kotlin.collections.Iterable"), "listOf", "emptyList"),
        MutableList("toMutableList()", FqName("kotlin.collections.MutableList")),
        Array("toTypedArray()", FqName("kotlin.Array"), "arrayOf", "emptyArray"),
        Sequence("asSequence()", FqName("kotlin.sequences.Sequence"), "sequenceOf", "emptySequence"),
        Set("toSet()", FqName("kotlin.collections.Set"), "setOf", "emptySet"),

        //specialized types must be last because iteration order is relevant for getCollectionType
        ArrayViaList("toList().toTypedArray()", FqName("kotlin.Array"), nameOverride = "Array"),
        ;

        val displayName get() = nameOverride ?: name

        fun specializeFor(sourceType: CollectionType) = when {
            this == Array && sourceType == Sequence -> ArrayViaList
            this == Array && sourceType == Iterable -> ArrayViaList
            else -> this
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.0", type.displayName)

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation {
        return Presentation.of(KotlinBundle.message("convert.expression.to.0.by.inserting.1", type.displayName, type.functionCall))
    }

    override fun invoke(actionContext: ActionContext, element: KtExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(actionContext.project)

        val replaced = element.replaced(psiFactory.createExpressionByPattern("$0.$1", element, type.functionCall))
        updater.moveCaretTo(replaced.endOffset)
    }
}