// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.createFromUsage.setupEditorSelection
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateFromUsageUtil
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.TransformToJavaUtil
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

/**
 * Information of new callable to create. Since we want to avoid the use of AA on EDT i.e., [CreateKotlinCallablePsiEditor],
 * we analyze the callable to create in advance, and we pass the information in [String] data.
 * [CreateKotlinCallablePsiEditor] will create and update PSI based on the [String] data. For example,
 * [definitionAsString] is a string form of callable body.
 */
internal data class NewCallableInfo(
    val definitionAsString: String,
    val parameterCandidates: List<CreateKotlinCallableAction.ParamCandidate>,
    val candidatesOfRenderedReturnType: List<String>,
    val containerClassFqName: FqName?,
    val isForCompanion: Boolean,
    val typeParameterCandidates: List<CreateKotlinCallableAction.ParamCandidate>,
    val superClassCandidates: List<String>,
)

/**
 * A class to update PSI for [CreateKotlinCallableAction] with the template support. It sets up the template to select
 * parameter names, parameter types, and the return type.
 */
internal class CreateKotlinCallablePsiEditor(
    private val project: Project,
    private val callableInfo: NewCallableInfo,
) {
    fun showEditor(declaration: KtNamedDeclaration, anchor: PsiElement, isExtension: Boolean, targetClass: PsiElement?, insertContainer: PsiElement, elementToReplace: PsiElement? = null) {
        val containerMaybeCompanion = if (callableInfo.isForCompanion) {
            if (insertContainer is KtClass) {
                insertContainer.getOrCreateCompanionObject()
            } else {
                val ktClass = targetClass as? KtClass
                if (ktClass != null) {
                    val hasCompanionObject = ktClass.companionObjects.isNotEmpty()
                    val companion = ktClass.getOrCreateCompanionObject()
                    if (!hasCompanionObject && isExtension) {
                        companion.body?.delete()
                    }
                }
                insertContainer
            }
        } else insertContainer

        val added: PsiElement
        if (targetClass is PsiClass) {
            val fqName = callableInfo.containerClassFqName ?: FqName.ROOT
            added = TransformToJavaUtil.convertToJava(declaration, fqName, targetClass) ?: return
        }
        else {
            added = if (elementToReplace != null && elementToReplace.isValid) {
                elementToReplace.replace(declaration) as PsiElement
            } else {
                CreateFromUsageUtil.placeDeclarationInContainer(declaration, containerMaybeCompanion, anchor)
            }
        }

        val psiProcessed = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(added) ?: return
        runTemplate(psiProcessed)
    }

    private fun moveCaretToCallable(editor: Editor, declaration: PsiElement) {
        val caretModel = editor.caretModel
        caretModel.moveToOffset(declaration.startOffset)
    }

    private fun getDocumentManager() = PsiDocumentManager.getInstance(project)

    private fun runTemplate(function: PsiElement) {
        val file = function.containingFile
        val editor = EditorHelper.openInMaybeInjectedEditor(file) ?: return
        val functionMarker = editor.document.createRangeMarker(function.textRange)
        moveCaretToCallable(editor, function)
        val template = setupTemplate(function)
        val listener = buildTemplateListener(editor, file, functionMarker)
        TemplateManager.getInstance(project).startTemplate(editor, template, listener)
    }

    private fun setupTemplate(declaration: PsiElement): Template {
        val builder = TemplateBuilderImpl(declaration)
        if (declaration is KtCallableDeclaration) {
            declaration.valueParameters.forEachIndexed { index, parameter -> builder.setupParameter(index, parameter) }
            // Set up template for the return type
            val returnType = declaration.typeReference
            if (returnType != null) builder.replaceElement(returnType, ExpressionForCreateCallable(callableInfo.candidatesOfRenderedReturnType))
        }
        if (declaration is KtClassOrObject) {
            declaration.primaryConstructorParameters.forEachIndexed { index, parameter -> builder.setupParameter(index, parameter) }
        }
        return builder.buildInlineTemplate()
    }

    private fun TemplateBuilderImpl.setupParameter(parameterIndex: Int, parameter: KtParameter) {
        // Set up template for the parameter name:
        val nameIdentifier = parameter.nameIdentifier ?: return
        replaceElement(
            nameIdentifier, ParameterNameExpression(parameterIndex, callableInfo.parameterCandidates[parameterIndex].names.toList())
        )

        // Set up template for the parameter type:
        val parameterTypeElement = parameter.typeReference ?: return
        replaceElement(parameterTypeElement, ExpressionForCreateCallable(callableInfo.parameterCandidates[parameterIndex].renderedTypes))
    }

    private fun buildTemplateListener(editor: Editor, file: PsiFile, functionMarker: RangeMarker): TemplateEditingAdapter {
        return object : TemplateEditingAdapter() {
            private fun finishTemplate(brokenOff: Boolean) {
                getDocumentManager().commitDocument(editor.document)
                if (brokenOff && !isUnitTestMode()) return
                updateCallableBody()
            }

            private fun getPointerToNewCallable() = PsiTreeUtil.findElementOfClassAtOffset(
                file,
                functionMarker.startOffset,
                KtNamedDeclaration::class.java,
                false
            )?.createSmartPointer()

            private fun updateCallableBody() {
                val pointerToNewCallable = getPointerToNewCallable() ?: return
                WriteCommandAction.writeCommandAction(project).run<Throwable> {
                    val newDecl = pointerToNewCallable.element ?: return@run
                    when (newDecl) {
                        is KtNamedFunction -> setupDeclarationBody(newDecl)
                        else -> Unit
                    }
                    CodeStyleManager.getInstance(project).reformat(newDecl)
                    setupEditorSelection(editor, newDecl)
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
