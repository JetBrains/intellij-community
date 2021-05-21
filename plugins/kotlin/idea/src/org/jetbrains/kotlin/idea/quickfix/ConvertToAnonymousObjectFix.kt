// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters3
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.SamConversionToAnonymousObjectIntention
import org.jetbrains.kotlin.idea.intentions.SamConversionToAnonymousObjectIntention.Companion.typeParameters
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.tower.WrongResolutionToClassifier
import org.jetbrains.kotlin.resolve.sam.getAbstractMembers
import org.jetbrains.kotlin.util.firstNotNullResult

class ConvertToAnonymousObjectFix(element: KtNameReferenceExpression) : KotlinQuickFixAction<KtNameReferenceExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("convert.to.anonymous.object")

    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val nameReference = element ?: return
        val call = nameReference.parent as? KtCallExpression ?: return
        val lambda = SamConversionToAnonymousObjectIntention.getLambdaExpression(call) ?: return
        val context = nameReference.analyze()
        val functionDescriptor = context.diagnostics.forElement(nameReference).firstNotNullResult {
            if (it.factory == Errors.RESOLUTION_TO_CLASSIFIER) getFunctionDescriptor(Errors.RESOLUTION_TO_CLASSIFIER.cast(it)) else null
        } ?: return
        val classDescriptor = functionDescriptor.containingDeclaration as? ClassDescriptor
        val lambdaDescriptor = context[BindingContext.FUNCTION, lambda.functionLiteral]
        val typeParameters = typeParameters(call, context, classDescriptor, functionDescriptor, lambdaDescriptor)
        SamConversionToAnonymousObjectIntention.convertToAnonymousObject(call, typeParameters, functionDescriptor, lambda)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtNameReferenceExpression>? {
            val casted = Errors.RESOLUTION_TO_CLASSIFIER.cast(diagnostic)
            if (casted.b != WrongResolutionToClassifier.INTERFACE_AS_FUNCTION) return null
            val nameReference = casted.psiElement as? KtNameReferenceExpression ?: return null
            val call = nameReference.parent as? KtCallExpression ?: return null
            if (SamConversionToAnonymousObjectIntention.getLambdaExpression(call) == null) return null
            if (getFunctionDescriptor(casted) == null) return null
            return ConvertToAnonymousObjectFix(nameReference)
        }

        private fun getFunctionDescriptor(
            d: DiagnosticWithParameters3<KtReferenceExpression, ClassifierDescriptor, WrongResolutionToClassifier, String>
        ): SimpleFunctionDescriptor? {
            val classDescriptor = d.a as? ClassDescriptor ?: return null
            val singleAbstractFunction = getAbstractMembers(classDescriptor).singleOrNull() as? SimpleFunctionDescriptor ?: return null
            return if (singleAbstractFunction.typeParameters.isEmpty()) singleAbstractFunction else null
        }
    }
}