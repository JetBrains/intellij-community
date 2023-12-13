// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

internal class CreateKotlinCallableAction(
    override val request: CreateMethodRequest,
    private val targetClass: JvmClass,
    private val abstract: Boolean,
    private val needFunctionBody: Boolean,
    private val myText: String,
    private val pointerToContainer: SmartPsiElementPointer<*>,
) : CreateKotlinElementAction(request, pointerToContainer), JvmGroupIntentionAction {
    private val callableDefinitionAsString = buildCallableAsString()

    override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return super.isAvailable(project, editor, file) && PsiNameHelper.getInstance(project)
            .isIdentifier(request.methodName) && callableDefinitionAsString != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, getContainerName(), "", callableDefinitionAsString ?: "")
    }

    override fun getRenderData() = JvmActionGroup.RenderData { request.methodName }

    override fun getTarget(): JvmClass = targetClass

    override fun getFamilyName(): String = message("create.method.from.usage.family")

    override fun getText(): String = myText

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        callableDefinitionAsString?.let { PsiUpdate(project, it, pointerToContainer).execute() }
    }

    private fun getContainerName(): String = pointerToContainer.element?.let { container ->
        when (container) {
            is KtClassOrObject -> container.name
            is KtFile -> container.name
            else -> null
        }
    } ?: ""

    private fun buildCallableAsString(): String? {
        val container = pointerToContainer.element as? KtElement ?: return null
        val modifierListAsString = container.getModifierListAsString()
        return analyze(container) {
            buildString {
                append(modifierListAsString)
                if (abstract) append("abstract")
                if (isNotEmpty()) append(" ")
                append(KtTokens.FUN_KEYWORD)
                append(" ")
                append(request.methodName)
                append("(")
                append(request.expectedParameters.mapIndexed { index, expectedParameter ->
                    expectedParameter.render(container, "p$index")
                }.joinToString())
                append(")")
                request.getRenderedType(container)?.let { append(": $it") }
                if (needFunctionBody) append(" {}")
            }
        }
    }

    private fun KtElement.getModifierListAsString(): String =
        KotlinModifierBuilder(this).apply { addJvmModifiers(request.modifiers) }.modifierList.text

    context (KtAnalysisSession)
    private fun ExpectedParameter.render(context: KtElement, alternativeParameterName: String): String = buildString {
        val parameterName = semanticNames.singleOrNull()
        append(parameterName ?: alternativeParameterName)
        append(": ")
        val parameterType = expectedTypes.singleOrNull()?.theType as? PsiType
        if (parameterType?.isValid() != true) {
            append("Any")
        } else {
            val ktType = parameterType.asKtType(context)
            append(ktType?.render(position = Variance.INVARIANT) ?: "Any")
        }
    }
}

private class PsiUpdate(
    private val project: Project,
    private val definitionAsString: String,
    private val pointerToContainer: SmartPsiElementPointer<*>,
) {

    fun execute() {
        val factory = KtPsiFactory(project)
        var function = factory.createFunction(definitionAsString)
        function = pointerToContainer.element?.let { function.addToContainer(it) } as? KtNamedFunction ?: return
        function = forcePsiPostprocessAndRestoreElement(function) ?: return
        setupTemplate(function)
    }

    private fun setupTemplate(function: KtNamedFunction) {
        //val parameters = request.expectedParameters
        //val typeExpressions = setupParameters(method, parameters).toTypedArray()
        //val nameExpressions = setupNameExpressions(parameters, project).toTypedArray()
        //val returnExpression = setupTypeElement(method, createConstraints(project, request.returnType))
        //createTemplateForMethod(typeExpressions, nameExpressions, method, targetClass, returnExpression, false, null)
    }

    private fun KtElement.addToContainer(container: PsiElement): PsiElement = when (container) {
        is KtClassOrObject -> {
            val classBody = container.getOrCreateBody()
            classBody.addBefore(this, classBody.rBrace)
        }

        else -> container.add(this)
    }
}