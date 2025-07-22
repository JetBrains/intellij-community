// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.name.JvmStandardClassIds.TRANSIENT_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

internal class RedundantNullableReturnTypeInspection :
    KotlinApplicableInspectionBase.Simple<KtCallableDeclaration, Unit>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }

        override fun visitProperty(property: KtProperty) {
            if (property.isVar) return
            visitTargetElement(property, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallableDeclaration): Boolean =
        !element.isExpectDeclaration() && !element.isEffectivelyActual()

    override fun getApplicableRanges(element: KtCallableDeclaration): List<TextRange> {
        val typeElement = element.typeReference?.typeElement as? KtNullableType ?: return emptyList()
        if (typeElement.innerType == null) return emptyList()
        val questionMark = typeElement.questionMarkNode as? LeafPsiElement ?: return emptyList()

        return listOf(questionMark.textRangeIn(element))
    }

    override fun KaSession.prepareContext(element: KtCallableDeclaration): Unit? {
        if (element.isOverridable() || hasJvmTransientAnnotation(element)) return null

        val actualReturnTypes = when (element) {
            is KtNamedFunction -> {
                val bodyExpression = element.bodyExpression ?: return null
                actualReturnTypes(bodyExpression, element)
            }

            is KtProperty -> {
                val initializer = element.initializer
                val getter = element.accessors.singleOrNull { it.isGetter }
                val getterBody = getter?.bodyExpression

                buildList {
                    if (initializer != null) addAll(actualReturnTypes(initializer, element))
                    if (getterBody != null) addAll(actualReturnTypes(getterBody, getter))
                }
            }

            else -> return null
        }

        if (actualReturnTypes.isEmpty() || actualReturnTypes.any { it.canBeNull }) return null

        return Unit
    }

    override fun getProblemDescription(element: KtCallableDeclaration, context: Unit): String =
        if (element is KtProperty)
            KotlinBundle.message("0.is.always.non.null.type", element.nameAsSafeName)
        else
            KotlinBundle.message("0.always.returns.non.null.type", element.nameAsSafeName)

    override fun createQuickFix(
        element: KtCallableDeclaration,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallableDeclaration> = object : KotlinModCommandQuickFix<KtCallableDeclaration>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("make.not.nullable")

        override fun applyFix(project: Project, element: KtCallableDeclaration, updater: ModPsiUpdater) {
            val typeElement = element.typeReference?.typeElement as? KtNullableType ?: return
            val innerType = typeElement.innerType ?: return
            typeElement.replace(innerType)
        }
    }
}

private fun KaSession.actualReturnTypes(
    expression: KtExpression,
    declaration: KtDeclaration,
): List<KaType> {
    val returnTypes = expression.collectDescendantsOfType<KtReturnExpression> {
        it.targetSymbol == declaration.symbol
    }.map {
        it.returnedExpression?.expressionType
    }

    return if (expression is KtBlockExpression) {
        returnTypes
    } else {
        returnTypes + expression.expressionType
    }.filterNotNull()
}

private fun KaSession.hasJvmTransientAnnotation(declaration: KtCallableDeclaration): Boolean {
    val symbol = (declaration.symbol as? KaPropertySymbol)?.backingFieldSymbol ?: return false
    return symbol.annotations.any {
        it.classId?.asSingleFqName() == TRANSIENT_ANNOTATION_FQ_NAME
    }
}
