// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.createFromUsage.setupEditorSelection
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Information of new callable to create. Since we want to avoid the use of AA on EDT i.e., [CreateKotlinCallablePsiEditor],
 * we analyze the callable to create in advance, and we pass the information in [String] data.
 * [CreateKotlinCallablePsiEditor] will create and update PSI based on the [String] data. For example,
 * [definitionAsString] is a string form of callable body.
 */
internal data class NewCallableInfo(
    val definitionAsString: String,
    val candidatesOfParameterNames: List<MutableCollection<String>>,
    val candidatesOfRenderedParameterTypes: List<List<String>>,
    val candidatesOfRenderedReturnType: List<String>,
    val containerClassFqName: FqName?,
)

/**
 * A class to update PSI for [CreateKotlinCallableAction] with the template support. It sets up the template to select
 * parameter names, parameter types, and the return type.
 */
internal class CreateKotlinCallablePsiEditor(
    private val project: Project,
    private val pointerToContainer: SmartPsiElementPointer<*>,
    private val callableInfo: NewCallableInfo,
) {
    fun execute() {
        val factory = KtPsiFactory(project)
        var function = factory.createFunction(callableInfo.definitionAsString)
        function = pointerToContainer.element?.let { function.addToContainer(it) } as? KtNamedFunction ?: return
        function = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function) ?: return
        runTemplate(function)
    }

    private fun moveCaretToCallable(editor: Editor, function: KtCallableDeclaration) {
        val caretModel = editor.caretModel
        caretModel.moveToOffset(function.startOffset)
    }

    private fun getDocumentManager() = PsiDocumentManager.getInstance(project)

    private fun runTemplate(function: KtNamedFunction) {
        val file = function.containingKtFile
        val editor = EditorHelper.openInEditor(file)
        val functionMarker = editor.document.createRangeMarker(function.textRange)
        moveCaretToCallable(editor, function)
        val templateImpl = setupTemplate(function)
        TemplateManager.getInstance(project)
            .startTemplate(editor, templateImpl, buildTemplateListener(editor, file, functionMarker))
    }

    private fun setupTemplate(function: KtNamedFunction): TemplateImpl {
        val builder = TemplateBuilderImpl(function)
        function.valueParameters.forEachIndexed { index, parameter -> builder.setupParameter(index, parameter) }

        // Set up template for the return type
        val returnType = function.typeReference
        if (returnType != null) builder.replaceElement(returnType, ExpressionForCreateCallable(callableInfo.candidatesOfRenderedReturnType))

        return builder.buildInlineTemplate() as TemplateImpl
    }

    private fun TemplateBuilderImpl.setupParameter(parameterIndex: Int, parameter: KtParameter) {
        // Set up template for the parameter name:
        val nameIdentifier = parameter.nameIdentifier ?: return
        replaceElement(
            nameIdentifier, ParameterNameExpression(parameterIndex, callableInfo.candidatesOfParameterNames[parameterIndex].toList())
        )

        // Set up template for the parameter type:
        val parameterTypeElement = parameter.typeReference ?: return
        replaceElement(parameterTypeElement, ExpressionForCreateCallable(callableInfo.candidatesOfRenderedParameterTypes[parameterIndex]))
    }

    private fun KtElement.addToContainer(container: PsiElement): PsiElement = when (container) {
        is KtClassOrObject -> {
            val classBody = container.getOrCreateBody()
            classBody.addBefore(this, classBody.rBrace)
        }

        else -> container.add(this)
    }

    private fun buildTemplateListener(editor: Editor, file: KtFile, functionMarker: RangeMarker): TemplateEditingAdapter {
        return object : TemplateEditingAdapter() {
            private fun finishTemplate(brokenOff: Boolean) {
                getDocumentManager().commitDocument(editor.document)
                if (brokenOff && !isUnitTestMode()) return
                updateCallableBody()
            }

            private fun getPointerToNewCallable() = PsiTreeUtil.findElementOfClassAtOffset(
                file,
                functionMarker.startOffset,
                KtCallableDeclaration::class.java,
                false
            )?.createSmartPointer()

            private fun updateCallableBody() {
                val pointerToNewCallable = getPointerToNewCallable() ?: return
                WriteCommandAction.writeCommandAction(project).run<Throwable> {
                    val newCallable = pointerToNewCallable.element ?: return@run
                    when (newCallable) {
                        is KtNamedFunction -> setupDeclarationBody(newCallable)
                        else -> TODO("Handle other cases.")
                    }
                    CodeStyleManager.getInstance(project).reformat(newCallable)
                    setupEditorSelection(editor, newCallable)
                }
            }

            private fun setupDeclarationBody(func: KtDeclarationWithBody) {
                if (func !is KtNamedFunction && func !is KtPropertyAccessor) return
                val oldBody = func.bodyExpression ?: return
                val bodyText = getFunctionBodyTextFromTemplate(
                    func.project,
                    TemplateKind.FUNCTION,
                    func.name,
                    (func as? KtFunction)?.typeReference?.text ?: "",
                    callableInfo.containerClassFqName
                )
                oldBody.replace(KtPsiFactory(func.project).createBlock(bodyText))
            }

            override fun templateCancelled(template: Template?) {
                finishTemplate(true)
            }

            override fun templateFinished(template: Template, brokenOff: Boolean) {
                finishTemplate(brokenOff)
            }
        }
    }
}
