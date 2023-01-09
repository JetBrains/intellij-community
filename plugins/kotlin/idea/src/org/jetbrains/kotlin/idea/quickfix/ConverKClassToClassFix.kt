// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeAttributes
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class ConvertKClassToClassFix(element: KtExpression) : KotlinQuickFixAction<KtExpression>(element), HighPriorityAction {
    override fun getText() = familyName
    override fun getFamilyName() = KotlinBundle.message("convert.from.class.to.kclass")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val expressionToInsert = KtPsiFactory(project).createExpressionByPattern("$0.java", element)
        element.replaced(expressionToInsert)
    }

    companion object {
        fun create(file: KtFile, expectedType: KotlinType, expressionType: KotlinType, diagnosticElement: KtExpression): ConvertKClassToClassFix?{
            val expressionClassDescriptor = expressionType.constructor.declarationDescriptor as? ClassDescriptor ?: return null
            if (!KotlinBuiltIns.isKClass(expressionClassDescriptor) || !expectedType.isJClass()) return null
            val expressionTypeArgument = expressionType.arguments.firstOrNull()?.type ?: return null
            val javaLangClassDescriptor = file.resolveImportReference(JAVA_LANG_CLASS_FQ_NAME)
                .singleOrNull() as? ClassDescriptor ?: return null
            val javaLangClassType = KotlinTypeFactory.simpleNotNullType(
                TypeAttributes.Empty,
                javaLangClassDescriptor,
                listOf(TypeProjectionImpl(expressionTypeArgument))
            )
            if (javaLangClassType.isSubtypeOf(expectedType)) {
                return ConvertKClassToClassFix(diagnosticElement)
            }
            return null
        }
    }
}