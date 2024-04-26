// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

object MovePropertyToConstructorUtils {
    fun KtProperty.moveToConstructor(info: MovePropertyToConstructorInfo) {
        val property = this
        val factory = KtPsiFactory.contextual(property, markGenerated = true)
        val commentSaver = CommentSaver(property)

        val newParameter = when (info) {
            is MovePropertyToConstructorInfo.ReplacementParameter-> {
                val constructorParameter =
                    info.constructorParameterToReplace.dereference() ?: return
                val parameterText = property.buildReplacementConstructorParameterText(constructorParameter, info.propertyAnnotationsText)
                constructorParameter.replace(factory.createParameter(parameterText))
            }

            is MovePropertyToConstructorInfo.AdditionalParameter -> {
                val containingClass = property.getStrictParentOfType<KtClass>() ?: return
                val parameterText =
                    property.buildAdditionalConstructorParameterText(info.parameterTypeText, info.propertyAnnotationsText)
                containingClass.createPrimaryConstructorParameterListIfAbsent().addParameter(factory.createParameter(parameterText)).apply {
                    ShortenReferencesFacility.getInstance().shorten(this)
                }
            }
        }

        commentSaver.restore(newParameter)
        property.delete()
    }

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

    fun KtProperty.buildReplacementConstructorParameterText(constructorParameter: KtParameter, propertyAnnotationsText: String?): String =
        buildConstructorParameterText(
            propertyAnnotationsText = propertyAnnotationsText,
            parameterAnnotationsText = constructorParameter.modifierList?.annotationEntries?.joinToString(separator = " ") { it.text },
            isVarArg = constructorParameter.isVarArg,
            typeText = constructorParameter.typeReference?.text,
            defaultValueText = constructorParameter.defaultValue?.text,
        )

    fun KtProperty.buildAdditionalConstructorParameterText(typeText: String, propertyAnnotationsText: String?): String =
        buildConstructorParameterText(
            propertyAnnotationsText = propertyAnnotationsText,
            typeText = typeText,
            defaultValueText = initializer?.text
        )

    private fun KtProperty.buildConstructorParameterText(
        propertyAnnotationsText: String? = null,
        parameterAnnotationsText: String? = null,
        isVarArg: Boolean = false,
        typeText: String? = null,
        defaultValueText: String? = null,
    ) = buildString {
        propertyAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
        parameterAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
        modifierList?.getModifiersText()?.let { appendWithSpaceBefore(it) }
        if (isVarArg) appendWithSpaceBefore(KtTokens.VARARG_KEYWORD.value)
        appendWithSpaceBefore(valOrVarKeyword.text)
        name?.let { appendWithSpaceBefore(it) }
        typeText?.let { append(": $it") }
        defaultValueText?.let { append(" = $it") }
    }

    private fun StringBuilder.appendWithSpaceBefore(str: String) = append(' ').append(str)

    private fun KtModifierList.getModifiers(): List<PsiElement> =
        node.getChildren(null).filter { it.elementType is KtModifierKeywordToken }.map { it.psi }

    private fun KtModifierList.getModifiersText() = getModifiers().joinToString(separator = " ") { it.text }
}