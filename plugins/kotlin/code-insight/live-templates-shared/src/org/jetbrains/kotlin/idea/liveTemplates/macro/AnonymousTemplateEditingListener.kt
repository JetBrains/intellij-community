// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.psi.KtReferenceExpression

internal class AnonymousTemplateEditingListener(private val psiFile: PsiFile, private val editor: Editor) : TemplateEditingAdapter() {
    private var subtypeInfo: SubtypeInfo? = null

    private class SubtypeInfo(val reference: KtReferenceExpression, val kind: KtClassKind, val hasZeroParameterConstructors: Boolean)

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun currentVariableChanged(templateState: TemplateState, template: Template?, oldIndex: Int, newIndex: Int) {
        subtypeInfo = null

        if (templateState.template == null) {
            return
        }

        val variableRange = templateState.getVariableRange("SUPERTYPE") ?: return
        val identifier = psiFile.findElementAt(variableRange.startOffset) ?: return
        val referenceExpression = identifier.parent as? KtReferenceExpression ?: return

        allowAnalysisOnEdt {
            analyze(referenceExpression) {
                subtypeInfo = resolveSubtypeInfo(referenceExpression)
            }
        }
    }

    private fun KtAnalysisSession.resolveSubtypeInfo(referenceExpression: KtReferenceExpression): SubtypeInfo? {
        val referencedClasses = sequence {
            for (symbol in referenceExpression.mainReference.resolveToSymbols()) {
                if (symbol is KtNamedClassOrObjectSymbol) {
                    yield(symbol)
                } else if (symbol is KtConstructorSymbol) {
                    val containingClassSymbol = symbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol
                    if (containingClassSymbol != null) {
                        yield(containingClassSymbol)
                    }
                }
            }
        }

        val referencedClass = referencedClasses.firstOrNull() ?: return null

        val hasZeroParameterConstructors = referencedClass
            .getDeclaredMemberScope()
            .getConstructors()
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

        if (subtypeInfo.kind == KtClassKind.CLASS) {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)

            if (document != null) {
                runWriteAction {
                    val insertionOffset = classReference.textRange.endOffset
                    document.insertString(insertionOffset, "()")
                    editor.caretModel.moveToOffset(insertionOffset + if (subtypeInfo.hasZeroParameterConstructors) 2 else 1)
                }
            }
        }
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
