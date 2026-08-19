// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isNullable
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal object KotlinTimeToJavaInstantFixFactories {
    private val JAVA_TIME_INSTANT: ClassId = ClassId.topLevel(FqName("java.time.Instant"))
    private val KOTLIN_TIME_INSTANT: ClassId = ClassId.topLevel(FqName("kotlin.time.Instant"))
    private val KOTLINX_DATETIME_INSTANT: ClassId = ClassId.topLevel(FqName("kotlinx.datetime.Instant"))

    private val KOTLIN_TIME_TO_JAVA_INSTANT: FqName = FqName("kotlin.time.toJavaInstant")
    private val KOTLINX_DATETIME_TO_JAVA_INSTANT: FqName = FqName("kotlinx.datetime.toJavaInstant")

    private val substitutionMap = mapOf(
        KOTLIN_TIME_INSTANT to KOTLIN_TIME_TO_JAVA_INSTANT,
        KOTLINX_DATETIME_INSTANT to KOTLINX_DATETIME_TO_JAVA_INSTANT,
    )

    val argumentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        listOfNotNull(createFix(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType))
    }

    val returnTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        listOfNotNull(createFix(diagnostic.psi, diagnostic.expectedType, diagnostic.actualType))
    }

    val initializerTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
        val initializerExpression = diagnostic.initializer ?: return@ModCommandBased emptyList()
        listOfNotNull(createFix(initializerExpression, diagnostic.expectedType, diagnostic.actualType))
    }

    val assignmentTypeMismatchFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
        listOfNotNull(createFix(diagnostic.expression, diagnostic.expectedType, diagnostic.actualType))
    }

    context(session: KaSession)
    private fun createFix(
        psiElement: PsiElement,
        expectedType: KaType,
        actualType: KaType,
    ): ToJavaInstantFix? {
        if (psiElement !is KtExpression) return null
        if (!expectedType.isSubtypeOf(JAVA_TIME_INSTANT)) return null
        if (actualType.isNullable && !expectedType.isNullable) return null

        val conversionFunction = substitutionMap.entries.firstOrNull { actualType.isSubtypeOf(it.key) }?.value ?: return null

        return ToJavaInstantFix(
            element = psiElement,
            context = ToJavaInstantFix.Context(replacementFqName = conversionFunction, wasNullable = actualType.isNullable),
        )
    }
}

private class ToJavaInstantFix(
    element: KtExpression,
    context: Context,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, ToJavaInstantFix.Context>(element, context) {
    class Context(
        val replacementFqName: FqName,
        val wasNullable: Boolean,
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        element.containingKtFile.addImport(elementContext.replacementFqName)
        val callShortName = elementContext.replacementFqName.shortName().asString()

        val navigationOperator = if (elementContext.wasNullable) "?." else "."
        val replaced = element.replaced(
            KtPsiFactory.contextual(element).createExpression("(${element.text})${navigationOperator}${callShortName}()")
        ) as? KtQualifiedExpression ?: return
        val receiver = replaced.receiverExpression as? KtParenthesizedExpression ?: return
        if (KtPsiUtil.areParenthesesUseless(receiver)) {
            receiver.expression?.let { receiver.replace(it) }
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtExpression): Presentation? {
        return super.getActionPresentation(context, element)?.withPriority(PriorityAction.Priority.HIGH)
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message(
            "fix.kotlin.time.to.java.instant.family.convert.to.0",
            java.time.Instant::class.java.canonicalName,
        )
    }
}
