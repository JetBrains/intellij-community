// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.ide.DataManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.endOffset
import com.intellij.refactoring.rename.RenamerFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly


object MoveMemberToCompanionObjectUtils {
    fun KtNamedDeclaration.invokeRenameOnAddedParameter(parameterName: String?, editor: Editor?) {
        if (editor == null || parameterName == null) return
        val addedParameter = when (this) {
            is KtClassOrObject -> {
                primaryConstructorParameters.firstOrNull()?.takeIf { it.name == parameterName }
            }
            is KtNamedFunction -> {
                valueParameters.firstOrNull()?.takeIf { it.name == parameterName }
            }
            else -> null
        } ?: return

        editor.caretModel.moveToOffset(addedParameter.nameIdentifier?.endOffset ?: return)

        val context = DataManager.getInstance().getDataContext(editor.component)
        val renamer = RenamerFactory.EP_NAME.extensionList
            .flatMap { it.createRenamers(context) }
            .firstOrNull()
        if (renamer == null) return
        renamer.performRename()
    }

    fun KtNamedDeclaration.suggestInstanceName(): String? {
        val suggestedName = containingClass()?.takeIf {
            (this !is KtClass || this.isInner()) && this !is KtProperty
        }?.name?.decapitalizeAsciiOnly() ?: return null

        val usedNames = mutableSetOf<String>()
        accept(object : KtTreeVisitorVoid() {
            override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                super.visitNamedDeclaration(declaration)
                usedNames.add(declaration.nameAsSafeName.asString())
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)
                expression.getIdentifier()?.text?.let { usedNames.add(it) }
            }
        })

        if (suggestedName !in usedNames) return suggestedName
        for (i in 1..1000) {
            val nameWithNum = "$suggestedName$i"
            if (nameWithNum !in usedNames) return nameWithNum
        }
        return null
    }
}