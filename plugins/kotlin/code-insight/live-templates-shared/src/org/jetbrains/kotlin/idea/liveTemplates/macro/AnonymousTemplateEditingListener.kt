// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.declaredMemberScope
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.idea.codeInsight.overrideImplement.OverrideImplementFacility
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtReferenceExpression

internal class AnonymousTemplateEditingListener(private val psiFile: PsiFile, private val editor: Editor) : TemplateEditingAdapter() {
    private var subtypeInfo: SubtypeInfo? = null

    private class SubtypeInfo(val reference: KtReferenceExpression, val kind: KaClassKind, val hasZeroParameterConstructors: Boolean)

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun currentVariableChanged(templateState: TemplateState, template: Template?, oldIndex: Int, newIndex: Int) {
        subtypeInfo = null

        if (templateState.template == null) {
            return
        }

        val variableRange = templateState.getVariableRange("SUPERTYPE") ?: return
        val identifier = psiFile.findElementAt(variableRange.startOffset) ?: return
        val referenceExpression = identifier.parent as? KtReferenceExpression ?: return

        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(referenceExpression) {
                    subtypeInfo = resolveSubtypeInfo(referenceExpression)
                }
            }
        }
    }

    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    private fun resolveSubtypeInfo(referenceExpression: KtReferenceExpression): SubtypeInfo? {
        val referencedClasses = sequence {
            for (symbol in referenceExpression.mainReference.resolveToSymbols()) {
                if (symbol is KaNamedClassSymbol) {
                    yield(symbol)
                } else if (symbol is KaConstructorSymbol) {
                    val containingClassSymbol = symbol.containingDeclaration as? KaNamedClassSymbol
                    if (containingClassSymbol != null) {
                        yield(containingClassSymbol)
                    }
                }
            }
        }

        val referencedClass = referencedClasses.firstOrNull() ?: return null

        val hasZeroParameterConstructors = referencedClass
            .declaredMemberScope
            .constructors
            .any { ctor ->
                val parameters = ctor.valueParameters
                parameters.isEmpty() || parameters.all { it.hasDefaultValue }
            }

        return SubtypeInfo(referenceExpression, referencedClass.classKind, hasZeroParameterConstructors)
    }

    override fun templateFinished(template: Template, brokenOff: Boolean) {
        editor.putUserData(LISTENER_KEY, null)
        if (brokenOff) return

        val subtypeInfo = this.subtypeInfo ?: return
        val classReference = subtypeInfo.reference

        if (subtypeInfo.kind == KaClassKind.CLASS) {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)

            if (document != null) {
                runWriteAction {
                    val insertionOffset = classReference.textRange.endOffset
                    document.insertString(insertionOffset, "()")
                    editor.caretModel.moveToOffset(insertionOffset + if (subtypeInfo.hasZeroParameterConstructors) 2 else 1)
                }
            }
        }

        OverrideImplementFacility.getInstance().implement(editor, psiFile, true)
    }

    companion object {
        private val LISTENER_KEY = Key.create<AnonymousTemplateEditingListener>("kotlin.AnonymousTemplateEditingListener")

        fun registerListener(editor: Editor, project: Project) {
            if (editor.getUserData(LISTENER_KEY) != null) return

            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
            val templateState = TemplateManagerImpl.getTemplateState(editor) ?: return

            val listener = AnonymousTemplateEditingListener(psiFile, editor)
            editor.putUserData(LISTENER_KEY, listener)
            templateState.addTemplateStateListener(listener)
        }
    }
}
