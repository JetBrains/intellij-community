// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext

class InlineTypeParameterFix(val typeReference: KtTypeReference) : KotlinQuickFixAction<KtTypeReference>(typeReference) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val parameter = typeReference.parent as? KtTypeParameter ?: return
        val bound = parameter.extendsBound ?: return
        val parameterList = parameter.parent as? KtTypeParameterList ?: return
        val parameterListOwner = typeReference.getStrictParentOfType<KtTypeParameterListOwner>() ?: return
        val context = parameterListOwner.analyzeWithContent()
        val parameterDescriptor = context[BindingContext.TYPE_PARAMETER, parameter] ?: return
        parameterListOwner.forEachDescendantOfType<KtTypeReference> { typeReference ->
            val typeElement = typeReference.typeElement
            val type = context[BindingContext.TYPE, typeReference]
            if (typeElement != null && type != null && type.constructor.declarationDescriptor == parameterDescriptor) {
                typeReference.replace(bound)
            }
        }

        if (parameterList.parameters.size == 1) {
            parameterList.delete()
        } else {
            EditCommaSeparatedListHelper.removeItem(parameter)
        }
    }

    override fun getText() = KotlinBundle.message("inline.type.parameter")

    override fun getFamilyName() = text

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) = InlineTypeParameterFix(Errors.FINAL_UPPER_BOUND.cast(diagnostic).psiElement)
    }
}