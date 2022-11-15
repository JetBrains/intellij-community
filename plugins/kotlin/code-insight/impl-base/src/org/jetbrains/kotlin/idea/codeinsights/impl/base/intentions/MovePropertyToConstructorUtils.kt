// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.resolve.source.getPsi

object MovePropertyToConstructorUtils {
    fun KtProperty.isMovableToConstructorByPsi(): Boolean {
        fun KtProperty.isDeclaredInSupportedClass(): Boolean {
            val parent = getStrictParentOfType<KtClassOrObject>()
            return parent is KtClass &&
                    !parent.isInterface() &&
                    !parent.isExpectDeclaration() &&
                    parent.secondaryConstructors.isEmpty() &&
                    parent.primaryConstructor?.hasActualModifier() != true
        }

        return !isLocal
                && !hasDelegate()
                && getter == null
                && setter == null
                && !hasModifier(KtTokens.LATEINIT_KEYWORD)
                && isDeclaredInSupportedClass()
    }

    fun KtProperty.buildReplacementConstructorParameterText(constructorParameter: KtParameter, propertyAnnotationsText: String?): String {
        val parameterAnnotationsText =
            constructorParameter.modifierList?.annotationEntries?.joinToString(separator = " ") { it.text }

        return buildString {
            modifierList?.getModifiersText()?.let(this::append)
            propertyAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
            parameterAnnotationsText?.let { appendWithSpaceBefore(it) }
            if (constructorParameter.isVarArg) appendWithSpaceBefore(KtTokens.VARARG_KEYWORD.value)
            appendWithSpaceBefore(valOrVarKeyword.text)
            name?.let { appendWithSpaceBefore(it) }
            constructorParameter.typeReference?.text?.let { append(": $it") }
            constructorParameter.defaultValue?.text?.let { append(" = $it") }
        }
    }

    fun KtProperty.buildAdditionalConstructorParameterText(typeText: String, propertyAnnotationsText: String?): String = buildString {
        modifierList?.getModifiersText()?.let(this::append)
        propertyAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
        appendWithSpaceBefore(valOrVarKeyword.text)
        name?.let { appendWithSpaceBefore(it) }
        appendWithSpaceBefore(": $typeText")
        initializer?.text?.let { append(" = $it") }
    }

    private fun StringBuilder.appendWithSpaceBefore(str: String) = append(' ').append(str)

    private fun KtModifierList.getModifiers(): List<PsiElement> =
        node.getChildren(null).filter { it.elementType is KtModifierKeywordToken }.map { it.psi }

    private fun KtModifierList.getModifiersText() = getModifiers().joinToString(separator = " ") { it.text }
}