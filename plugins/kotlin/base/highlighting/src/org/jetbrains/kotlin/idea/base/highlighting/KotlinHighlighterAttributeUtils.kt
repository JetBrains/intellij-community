// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlighterAttributeUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

@ApiStatus.Internal
fun textAttributesKeyForKtElement(element: PsiElement): TextAttributesKey? {
    return sequence {
        yield(textAttributesKeyForTypeDeclaration(element))
        yield(textAttributesKeyForKtFunction(element))
        yield(textAttributesKeyForPropertyDeclaration(element))
    }.firstOrNull { it != null }
}

@ApiStatus.Internal
fun textAttributesKeyForPropertyDeclaration(declaration: PsiElement): TextAttributesKey? = when (declaration) {
    is KtProperty -> textAttributesForKtPropertyDeclaration(declaration)
    is KtParameter -> textAttributesForKtParameterDeclaration(declaration)
    is PsiLocalVariable -> Colors.LOCAL_VARIABLE
    is PsiParameter -> Colors.PARAMETER
    is PsiField -> Colors.INSTANCE_PROPERTY
    else -> null
}

@ApiStatus.Internal
fun textAttributesForKtParameterDeclaration(parameter: KtParameter): TextAttributesKey = when {
    parameter.valOrVarKeyword != null -> Colors.INSTANCE_PROPERTY
    else -> Colors.PARAMETER
}

@ApiStatus.Internal
fun textAttributesForKtPropertyDeclaration(property: KtProperty): TextAttributesKey? = when {
    property.isExtensionDeclaration() -> Colors.EXTENSION_PROPERTY
    property.isLocal -> Colors.LOCAL_VARIABLE
    property.isTopLevel -> when {
        property.isCustomPropertyDeclaration() -> Colors.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> Colors.PACKAGE_PROPERTY
    }
    else -> when {
        property.isCustomPropertyDeclaration() -> Colors.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> Colors.INSTANCE_PROPERTY
    }
}

@ApiStatus.Internal
fun textAttributesKeyForKtFunction(function: PsiElement): TextAttributesKey? = when (function) {
    is KtFunction -> Colors.FUNCTION_DECLARATION
    else -> null
}

@ApiStatus.Internal
fun textAttributesKeyForTypeDeclaration(declaration: PsiElement): TextAttributesKey? = when {
    declaration is KtTypeParameter || declaration is PsiTypeParameter -> Colors.TYPE_PARAMETER
    declaration is KtTypeAlias -> Colors.TYPE_ALIAS
    declaration is KtClass -> when {
        declaration.isAnnotation() -> Colors.ANNOTATION
        else -> textAttributesForClass(declaration)
    }
    declaration is PsiClass && declaration.isInterface && !declaration.isAnnotationType -> Colors.TRAIT
    declaration is KtPrimaryConstructor && declaration.containingClassOrObject?.isAnnotation() == true -> Colors.ANNOTATION
    declaration is KtObjectDeclaration -> Colors.OBJECT
    declaration is PsiEnumConstant -> Colors.ENUM_ENTRY
    declaration is PsiClass -> when {
        declaration.isAnnotationType -> Colors.ANNOTATION
        declaration.hasModifier(JvmModifier.ABSTRACT) -> Colors.ABSTRACT_CLASS
        else -> Colors.CLASS
    }
    else -> null
}

@ApiStatus.Internal
fun textAttributesForClass(klass: KtClass): TextAttributesKey = when {
    klass.isInterface() -> Colors.TRAIT
    klass.isAnnotation() -> Colors.ANNOTATION
    klass.isEnum() -> Colors.ENUM
    klass is KtEnumEntry -> Colors.ENUM_ENTRY
    klass.isAbstract() -> Colors.ABSTRACT_CLASS
    else -> Colors.CLASS
}

private fun KtProperty.isCustomPropertyDeclaration(): Boolean {
    return getter?.bodyExpression != null || setter?.bodyExpression != null
}