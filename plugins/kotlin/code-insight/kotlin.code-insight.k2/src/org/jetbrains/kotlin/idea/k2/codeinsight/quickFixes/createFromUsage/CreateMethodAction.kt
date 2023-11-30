// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.getOrCreateBody

internal class CreateMethodAction(
    targetClass: KtClassOrObject,
    override val request: CreateMethodRequest,
    private val abstract: Boolean
) : CreateMemberAction(targetClass, request), JvmGroupIntentionAction {

    override fun getActionGroup(): JvmActionGroup = if (abstract) CreateAbstractMethodActionGroup else CreateMethodActionGroup

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return super.isAvailable(project, editor, file) && PsiNameHelper.getInstance(project).isIdentifier(request.methodName)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val method = MethodRenderer(project, abstract, target, request).renderMethod()
        val className = myTargetPointer.element?.name
        return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, className, "", method.text)
    }

    override fun getRenderData() = JvmActionGroup.RenderData { request.methodName }

    override fun getFamilyName(): String = message("create.method.from.usage.family")

    override fun getText(): String {
        val what = request.methodName
        val where = myTargetPointer.element?.name!!
        return KotlinBundle.message("add.method.0.to.1", what, where)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        MethodRenderer(project, abstract, target, request).execute()
    }
}

private class MethodRenderer(
    val project: Project,
    val abstract: Boolean,
    val targetClass: KtClassOrObject,
    val request: CreateMethodRequest
) {

    fun execute() {
        var method = renderMethod()
        method = insertMethod(method)
        method = forcePsiPostprocessAndRestoreElement(method) ?: return
        setupTemplate(method)
    }

    private fun setupTemplate(method: KtNamedFunction) {
        //val parameters = request.expectedParameters
        //val typeExpressions = setupParameters(method, parameters).toTypedArray()
        //val nameExpressions = setupNameExpressions(parameters, project).toTypedArray()
        //val returnExpression = setupTypeElement(method, createConstraints(project, request.returnType))
        //createTemplateForMethod(typeExpressions, nameExpressions, method, targetClass, returnExpression, false, null)
    }

    fun renderMethod(): KtNamedFunction {
        val factory = KtPsiFactory(project)
        val modifierList = KotlinModifierBuilder(targetClass).apply { addJvmModifiers(request.modifiers) }.modifierList.text
        //todo insert annotations
        val methodPrototype = buildString {
            append(modifierList)
            if (isNotEmpty()) append(" ")
            append(KtTokens.FUN_KEYWORD)
            append(" ")
            append(request.methodName)
            append("()")
            if (!abstract) append("{}")
        }

        return factory.createFunction(methodPrototype)
    }

    private fun insertMethod(method: KtNamedFunction): KtNamedFunction {
        val body = targetClass.getOrCreateBody()

        //todo guess anchor
        return body.addBefore(method, body.rBrace) as KtNamedFunction
    }
}