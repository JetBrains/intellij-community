// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

private val kotlinIoPackage: FqName = FqName("kotlin.io")
private val readLineFqName = FqName("kotlin.io.readLine")
private val readLineName = Name.identifier("readLine")

class ReplaceReadLineWithReadlnInspection : KotlinApplicableInspectionBase.Simple<KtExpression, ReplaceReadLineWithReadlnInspection.Context>(), CleanupLocalInspectionTool {
    data class Context(
        val targetExpression: SmartPsiElementPointer<KtExpression>,
        val newFunctionName: String
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        callExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.readline.with.readln.display.name")

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtExpression,
        context: Context,
        isOnTheFly: Boolean
    ) {
        val ktExpression = context.targetExpression.element ?: return
        super.registerProblem(ranges, holder, ktExpression, context, isOnTheFly)
    }

    override fun createQuickFix(
        element: KtExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> =
        ReplaceFix(context.targetExpression, context.newFunctionName)

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        val callableId = analyze(element) {
            val resolvedCall = element.resolveToCall()?.singleFunctionCallOrNull()
            resolvedCall?.symbol?.callableId
        } ?: return null
        if (callableId.packageName != kotlinIoPackage || callableId.callableName != readLineName) return null

        val qualifiedOrCall = element.getQualifiedExpressionForSelectorOrThis()
        val parent = qualifiedOrCall.parent
        val (targetExpression, newFunctionName) = if (parent is KtPostfixExpression && parent.operationToken == KtTokens.EXCLEXCL) {
            parent to "readln"
        } else {
            qualifiedOrCall to "readlnOrNull"
        }
        return Context(targetExpression.createSmartPointer(), newFunctionName)
    }

    private class ReplaceFix(
        private val targetExpression: SmartPsiElementPointer<KtExpression>,
        private val functionName: String
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getName(): String = KotlinBundle.message("replace.with.0", functionName)

        override fun getFamilyName(): String = KotlinBundle.message("inspection.replace.readline.with.readln.display.name")

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val expression = targetExpression.element?.let(updater::getWritable) ?: return
            val replaced =
                expression.replace(KtPsiFactory(project).createExpression("kotlin.io.$functionName()")) as KtElement
            ShortenReferencesFacility.getInstance().shorten(replaced)
        }
    }
}
