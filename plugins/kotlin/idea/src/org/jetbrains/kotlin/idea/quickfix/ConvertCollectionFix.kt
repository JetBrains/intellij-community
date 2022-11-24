// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class ConvertCollectionFix(element: KtExpression, val type: CollectionType) : KotlinQuickFixAction<KtExpression>(element) {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.0", type.displayName)
    override fun getText() = KotlinBundle.message("convert.expression.to.0.by.inserting.1", type.displayName, type.functionCall)

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val psiFactory = KtPsiFactory(project)

        val replaced = expression.replaced(psiFactory.createExpressionByPattern("$0.$1", expression, type.functionCall))
        editor?.caretModel?.moveToOffset(replaced.endOffset)
    }

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

    companion object {
        private val TYPES = CollectionType.values()

        fun getConversionTypeOrNull(expressionType: KotlinType, expectedType: KotlinType): CollectionType? {
            val expressionCollectionType = expressionType.getCollectionType() ?: return null
            val expectedCollectionType = expectedType.getCollectionType() ?: return null
            if (expressionCollectionType == expectedCollectionType) return null

            val expressionTypeArg = expressionType.arguments.singleOrNull()?.type ?: return null
            val expectedTypeArg = expectedType.arguments.singleOrNull()?.type ?: return null
            if (!expressionTypeArg.isSubtypeOf(expectedTypeArg)) return null

            return expectedCollectionType.specializeFor(expressionCollectionType)
        }

        fun KotlinType.getCollectionType(acceptNullableTypes: Boolean = false): CollectionType? {
            if (isMarkedNullable && !acceptNullableTypes) return null
            return TYPES.firstOrNull { KotlinBuiltIns.isConstructedFromGivenClass(this, it.fqName) }
        }
    }
}
