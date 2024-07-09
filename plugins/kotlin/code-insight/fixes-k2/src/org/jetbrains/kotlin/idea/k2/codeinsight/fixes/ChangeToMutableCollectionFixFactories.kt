// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import kotlin.collections.get

internal object ChangeToMutableCollectionFixFactories {

    val noSetMethod = KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NoSetMethod> { diagnostic -> createQuickFixes(diagnostic) }

    private fun KaSession.createQuickFixes(diagnostic: KaFirDiagnostic.NoSetMethod): List<ModCommandAction> {
        val element = diagnostic.psi
        val arrayExpr = element.arrayExpression ?: return emptyList()
        val type = arrayExpr.expressionType as? KaClassType ?: return emptyList()
        if (!isReadOnlyListOrMap(type)) return emptyList()

        val property = arrayExpr.mainReference?.resolve() as? KtProperty ?: return emptyList()
        if (!isApplicable(property)) return emptyList()

        val typeName = type.classId.shortClassName.asString()

        return listOf(ChangeToMutableCollectionFix(property, ElementContext(typeName)))
    }

    /**
     * N.B. This check intentionally ignores `Set` type, because there are no
     * `set` operator on a `Set` - hence, no `NO_SET_METHOD` diagnostic ever reported.
     */
    private fun KaSession.isReadOnlyListOrMap(type: KaClassType): Boolean {
        return type.classId in listOf(
            StandardClassIds.List,
            StandardClassIds.Map,
        )
    }

    private fun isApplicable(property: KtProperty): Boolean {
        return property.isLocal && property.initializer != null
    }

    private class ElementContext(
        val typeName: String,
    )

    private class ChangeToMutableCollectionFix(property: KtProperty, context: ElementContext) :
        KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, ElementContext>(property, context) {

        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("fix.change.to.mutable.type.family")
        }

        override fun getPresentation(context: ActionContext, element: KtProperty): Presentation {
            val elementContext = getElementContext(context, element)
            return Presentation.of(KotlinBundle.message("fix.change.to.mutable.type.text", "Mutable${elementContext.typeName}"))
        }

        override fun invoke(actionContext: ActionContext, element: KtProperty, elementContext: ElementContext, updater: ModPsiUpdater) {
            val initializer = element.initializer ?: return
            analyze(initializer) {
                val type = initializer.expressionType as? KaClassType ?: return
                applyFix(element, type)
            }
            updater.moveCaretTo(element.endOffset)
        }
    }

    private fun KaSession.applyFix(property: KtProperty, type: KaClassType) {
        val initializer = property.initializer ?: return

        val fqName = initializer.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString()
        val mutableOf = mutableConversionMap[fqName]

        val psiFactory = KtPsiFactory(property.project)

        if (mutableOf != null) {
            (initializer as? KtCallExpression)?.calleeExpression?.replaced(psiFactory.createExpression(mutableOf)) ?: return
        } else {
            val toMutable = when (type.classId) {
                StandardClassIds.List -> "toMutableList"
                StandardClassIds.Set -> "toMutableSet"
                StandardClassIds.Map -> "toMutableMap"
                else -> null
            } ?: return

            val dotQualifiedExpression = initializer.replaced(
                psiFactory.createExpressionByPattern("($0).$1()", initializer, toMutable)
            ) as KtDotQualifiedExpression

            val receiver = dotQualifiedExpression.receiverExpression
            val deparenthesize = KtPsiUtil.deparenthesize(dotQualifiedExpression.receiverExpression)
            if (deparenthesize != null && receiver != deparenthesize) {
                receiver.replace(deparenthesize)
            }
        }

        property.typeReference?.also { it.replace(psiFactory.createType("Mutable${it.text}")) }
    }

    private const val COLLECTIONS: String = "kotlin.collections"

    private val mutableConversionMap: Map<String, String> = mapOf(
        "$COLLECTIONS.listOf" to "mutableListOf",
        "$COLLECTIONS.setOf" to "mutableSetOf",
        "$COLLECTIONS.mapOf" to "mutableMapOf",
    )
}
