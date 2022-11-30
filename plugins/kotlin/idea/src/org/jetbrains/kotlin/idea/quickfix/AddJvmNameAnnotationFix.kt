// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.psi.*

class AddJvmNameAnnotationFix(element: KtElement, private val jvmName: String) : KotlinQuickFixAction<KtElement>(element) {
    override fun getText(): String = if (element is KtAnnotationEntry) {
        KotlinBundle.message("fix.change.jvm.name")
    } else {
        KotlinBundle.message("fix.add.annotation.text.self", JvmFileClassUtil.JVM_NAME.shortName())
    }

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        when (element) {
            is KtAnnotationEntry -> {
                val argList = element.valueArgumentList
                val newArgList = KtPsiFactory(project).createCallArguments("(\"$jvmName\")")
                if (argList != null) {
                    argList.replace(newArgList)
                } else {
                    element.addAfter(newArgList, element.lastChild)
                }
            }
            is KtFunction ->
                element.addAnnotation(JvmFileClassUtil.JVM_NAME, annotationInnerText = "\"$jvmName\"")
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val function = diagnostic.psiElement as? KtNamedFunction ?: return null
            val functionName = function.name ?: return null
            val containingDeclaration = function.parent ?: return null
            val nameValidator = Fe10KotlinNewDeclarationNameValidator(
                containingDeclaration,
                function,
                KotlinNameSuggestionProvider.ValidatorTarget.FUNCTION
            )
            val receiverTypeElements = function.receiverTypeReference?.typeElements()?.joinToString("") { it.text } ?: ""
            val jvmName = Fe10KotlinNameSuggester.suggestNameByName(functionName + receiverTypeElements, nameValidator)
            return AddJvmNameAnnotationFix(function.findAnnotation(JvmFileClassUtil.JVM_NAME) ?: function, jvmName)
        }

        private fun KtTypeReference.typeElements(): List<KtTypeElement> {
            val typeElements = mutableListOf<KtTypeElement>()
            fun collect(typeReference: KtTypeReference?) {
                val typeElement = typeReference?.typeElement ?: return
                val typeArguments = typeElement.typeArgumentsAsTypes
                if (typeArguments.isEmpty()) {
                    typeElements.add(typeElement)
                } else {
                    typeArguments.forEach { collect(it) }
                }
            }
            typeElement?.typeArgumentsAsTypes?.forEach { collect(it) }
            return typeElements
        }
    }
}
