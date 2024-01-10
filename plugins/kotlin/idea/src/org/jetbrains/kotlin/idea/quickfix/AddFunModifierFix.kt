// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.inspections.RedundantSamConstructorInspection
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.sam.getAbstractMembers
import org.jetbrains.kotlin.resolve.source.getPsi

class AddFunModifierFix(
    element: KtClass,
    private val elementName: String,
    private val referrerCall: SmartPsiElementPointer<KtCallExpression>
) : AddModifierFixFE10(element, KtTokens.FUN_KEYWORD) {
    override fun getText() = KotlinBundle.message("add.fun.modifier.to.0", elementName)

    override fun getFamilyName() = text

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        super.invokeImpl(project, editor, file)
        referrerCall.element?.removeRedundantSamConstructor()
    }

    private fun KtCallExpression.removeRedundantSamConstructor() {
        if (lambdaArguments.singleOrNull() == null) return
        val argument = getStrictParentOfType<KtValueArgument>()?.takeIf { it.getArgumentExpression() == this } ?: return
        val parentCall = argument.getStrictParentOfType<KtCallExpression>() ?: return

        RedundantSamConstructorInspection.Util.replaceSamConstructorCall(this)

        if (parentCall.canMoveLambdaOutsideParentheses()) {
            runWriteActionIfPhysical(parentCall) {
                parentCall.moveFunctionLiteralOutsideParentheses()
            }
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = Errors.RESOLUTION_TO_CLASSIFIER.cast(diagnostic)
            val referrer = casted.psiElement
            if (referrer.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_4) return null

            val referrerCall = referrer.parent as? KtCallExpression ?: return null
            if (referrerCall.valueArguments.singleOrNull() !is KtLambdaArgument) return null

            val referenceClassDescriptor = casted.a as? ClassDescriptor ?: return null
            if (referenceClassDescriptor.isFun || !referenceClassDescriptor.isSamInterface()) return null

            val referenceClass = referenceClassDescriptor.source.getPsi() as? KtClass ?: return null
            val referenceClassName = referenceClass.name ?: return null
            return AddFunModifierFix(referenceClass, referenceClassName, referrerCall.createSmartPointer())
        }

        private fun ClassDescriptor.isSamInterface(): Boolean {
            if (kind != ClassKind.INTERFACE) return false
            val singleAbstractMember = getAbstractMembers(this).singleOrNull() ?: return false
            return singleAbstractMember is SimpleFunctionDescriptor && singleAbstractMember.typeParameters.isEmpty()
        }
    }
}
