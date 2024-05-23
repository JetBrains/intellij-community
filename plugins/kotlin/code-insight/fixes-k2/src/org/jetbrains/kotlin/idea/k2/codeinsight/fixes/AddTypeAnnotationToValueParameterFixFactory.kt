// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

internal object AddTypeAnnotationToValueParameterFixFactory {

    val addTypeAnnotationToValueParameterFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValueParameterWithNoTypeAnnotation ->
            val element = diagnostic.psi
            val defaultValue = element.defaultValue ?: return@ModCommandBased emptyList()
            val elementContext = getTypeName(element, defaultValue) ?: return@ModCommandBased emptyList()

            listOf(
                AddTypeAnnotationToValueParameterFix(element, elementContext)
            )
        }

    context(KtAnalysisSession)
    private fun getTypeName(element: KtParameter, defaultValue: KtExpression): String? {
        val type = defaultValue.getKtType() ?: return null

        if (type.isArrayOrPrimitiveArray()) {
            if (element.hasModifier(KtTokens.VARARG_KEYWORD)) {
                val elementType = type.getArrayElementType() ?: return null
                return getTypeName(elementType)
            } else if (defaultValue is KtCollectionLiteralExpression) {
                val elementType = type.getArrayElementType()
                if (elementType?.isPrimitive == true) {
                    val classId = (elementType as KtNonErrorClassType).classId
                    val arrayTypeName = "${classId.shortClassName}Array"
                    return arrayTypeName
                }
            }
        }
        return getTypeName(type)
    }

    context(KtAnalysisSession)
    private fun getTypeName(type: KtType): String {
        val typeName = type.render(
            KtTypeRendererForSource.WITH_SHORT_NAMES,
            Variance.INVARIANT
        )
        return typeName
    }

    private class AddTypeAnnotationToValueParameterFix(
        element: KtParameter,
        private val typeName: String,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtParameter, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtParameter,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            element.typeReference = KtPsiFactory(actionContext.project).createType(typeName)
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.add.type.annotation.family")

        override fun getActionName(
            actionContext: ActionContext,
            element: KtParameter,
            elementContext: Unit,
        ): String = KotlinBundle.message("fix.add.type.annotation.text", typeName, element.name.toString())
    }
}
