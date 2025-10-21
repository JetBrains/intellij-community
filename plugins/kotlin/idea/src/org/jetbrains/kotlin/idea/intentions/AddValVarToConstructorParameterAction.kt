// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyValPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.psi.mustHaveValOrVar
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.ValVarExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

interface AddValVarToConstructorParameterAction {
    fun canInvoke(element: KtParameter): Boolean =
        element.valOrVarKeyword == null && ((element.parent as? KtParameterList)?.parent as? KtPrimaryConstructor)?.takeIf { it.mustHaveValOrVar() || !it.isExpectDeclaration() } != null

    operator fun invoke(element: KtParameter, editor: Editor?) {
        val project = element.project

        element.addBefore(KtPsiFactory(project).createValKeyword(), element.nameIdentifier)

        if (element.containingClass()?.mustHaveOnlyValPropertiesInPrimaryConstructor() == true || editor == null) return

        val parameter = element.createSmartPointer().let {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            it.element
        } ?: return

        editor.caretModel.moveToOffset(parameter.startOffset)

        TemplateBuilderImpl(parameter)
            .apply { replaceElement(parameter.valOrVarKeyword ?: return@apply, ValVarExpression) }
            .buildInlineTemplate()
            .let { TemplateManager.getInstance(project).startTemplate(editor, it) }
    }

    class Intention : SelfTargetingRangeIntention<KtParameter>(
        KtParameter::class.java,
        KotlinBundle.messagePointer("add.val.var.to.primary.constructor.parameter")
    ), AddValVarToConstructorParameterAction {
        override fun applicabilityRange(element: KtParameter): TextRange? {
            if (!canInvoke(element)) return null
            val containingClass = element.getStrictParentOfType<KtClass>()
            if (containingClass?.mustHaveOnlyPropertiesInPrimaryConstructor() == true) {
                // this case is handled by a separate quickfix below
                return null
            }
            setTextGetter(KotlinBundle.messagePointer("add.val.var.to.parameter.0", element.name ?: ""))
            return element.nameIdentifier?.textRange
        }

        override fun applyTo(element: KtParameter, editor: Editor?): Unit = invoke(element, editor)
    }

    class QuickFix(parameter: KtParameter) :
      KotlinQuickFixAction<KtParameter>(parameter),
      AddValVarToConstructorParameterAction {
        override fun getText(): String {
            val element = this.element ?: return ""
            val key = if (element.getStrictParentOfType<KtClass>()?.mustHaveOnlyValPropertiesInPrimaryConstructor() == true ) {
                "add.val.to.parameter.0"
            } else {
                "add.val.var.to.parameter.0"
            }
            return KotlinBundle.message(key, element.name ?: "")
        }

        override fun getFamilyName(): String = KotlinBundle.message("add.val.var.to.primary.constructor.parameter")

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            invoke(element ?: return, editor)
        }
    }

    object DataClassConstructorNotPropertyQuickFixFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): QuickFix =
            QuickFix(Errors.DATA_CLASS_NOT_PROPERTY_PARAMETER.cast(diagnostic).psiElement)
    }

    object AnnotationClassConstructorNotValPropertyQuickFixFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): QuickFix =
            QuickFix(Errors.MISSING_VAL_ON_ANNOTATION_PARAMETER.cast(diagnostic).psiElement)
    }

    object ValueClassConstructorNotValPropertyQuickFixFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction {
            val parameter = Errors.VALUE_CLASS_CONSTRUCTOR_NOT_FINAL_READ_ONLY_PARAMETER.cast(diagnostic).psiElement
            return if (parameter.isMutable)
                ChangeVariableMutabilityFix(parameter, false).asIntention()
            else
                QuickFix(parameter)
        }
    }
}