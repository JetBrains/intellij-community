// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlighterAttributeUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.highlighter.KotlinNameHighlightInfoTypes
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

@ApiStatus.Internal
fun textAttributesKeyForKtElement(element: PsiElement): HighlightInfoType? {
    return sequence {
        yield(textAttributesKeyForTypeDeclaration(element))
        yield(textAttributesKeyForKtFunction(element))
        yield(textAttributesKeyForPropertyDeclaration(element))
    }.firstOrNull { it != null }
}

@ApiStatus.Internal
fun textAttributesKeyForPropertyDeclaration(declaration: PsiElement): HighlightInfoType? = when (declaration) {
    is KtProperty -> textAttributesForKtPropertyDeclaration(declaration)
    is KtParameter -> textAttributesForKtParameterDeclaration(declaration)
    is PsiLocalVariable -> KotlinNameHighlightInfoTypes.LOCAL_VARIABLE
    is PsiParameter -> KotlinNameHighlightInfoTypes.PARAMETER
    is PsiField -> KotlinNameHighlightInfoTypes.INSTANCE_PROPERTY
    else -> null
}

@ApiStatus.Internal
fun textAttributesForKtParameterDeclaration(parameter: KtParameter): HighlightInfoType = when {
    parameter.valOrVarKeyword != null -> KotlinNameHighlightInfoTypes.INSTANCE_PROPERTY
    else -> KotlinNameHighlightInfoTypes.PARAMETER
}

@ApiStatus.Internal
fun textAttributesForKtPropertyDeclaration(property: KtProperty): HighlightInfoType? = when {
    property.isExtensionDeclaration() -> KotlinNameHighlightInfoTypes.EXTENSION_PROPERTY
    property.isLocal -> KotlinNameHighlightInfoTypes.LOCAL_VARIABLE
    property.isTopLevel -> when {
        property.isCustomPropertyDeclaration() -> KotlinNameHighlightInfoTypes.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> KotlinNameHighlightInfoTypes.PACKAGE_PROPERTY
    }
    else -> when {
        property.isCustomPropertyDeclaration() -> KotlinNameHighlightInfoTypes.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> KotlinNameHighlightInfoTypes.INSTANCE_PROPERTY
    }
}

@ApiStatus.Internal
fun textAttributesKeyForKtFunction(function: PsiElement): HighlightInfoType? = when (function) {
    is KtFunction -> KotlinNameHighlightInfoTypes.FUNCTION_DECLARATION
    else -> null
}

@ApiStatus.Internal
fun textAttributesKeyForTypeDeclaration(declaration: PsiElement): HighlightInfoType? = when {
    declaration is KtTypeParameter || declaration is PsiTypeParameter -> KotlinNameHighlightInfoTypes.TYPE_PARAMETER
    declaration is KtTypeAlias -> KotlinNameHighlightInfoTypes.TYPE_ALIAS
    declaration is KtClass -> when {
        declaration.isAnnotation() -> KotlinNameHighlightInfoTypes.ANNOTATION
        else -> textAttributesForClass(declaration)
    }
    declaration is PsiClass && declaration.isInterface && !declaration.isAnnotationType -> KotlinNameHighlightInfoTypes.TRAIT
    declaration is KtPrimaryConstructor && declaration.containingClassOrObject?.isAnnotation() == true -> KotlinNameHighlightInfoTypes.ANNOTATION
    declaration is KtObjectDeclaration -> KotlinNameHighlightInfoTypes.OBJECT
    declaration is PsiEnumConstant -> KotlinNameHighlightInfoTypes.ENUM_ENTRY
    declaration is PsiClass -> when {
        declaration.isAnnotationType -> KotlinNameHighlightInfoTypes.ANNOTATION
        declaration.hasModifier(JvmModifier.ABSTRACT) -> KotlinNameHighlightInfoTypes.ABSTRACT_CLASS
        else -> KotlinNameHighlightInfoTypes.CLASS
    }
    else -> null
}

@ApiStatus.Internal
fun textAttributesForClass(klass: KtClass): HighlightInfoType = when {
    klass.isInterface() -> KotlinNameHighlightInfoTypes.TRAIT
    klass.isAnnotation() -> KotlinNameHighlightInfoTypes.ANNOTATION
    klass.isEnum() -> KotlinNameHighlightInfoTypes.ENUM
    klass is KtEnumEntry -> KotlinNameHighlightInfoTypes.ENUM_ENTRY
    klass.isAbstract() -> KotlinNameHighlightInfoTypes.ABSTRACT_CLASS
    else -> KotlinNameHighlightInfoTypes.CLASS
}

private fun KtProperty.isCustomPropertyDeclaration(): Boolean {
    return getter?.bodyExpression != null || setter?.bodyExpression != null
}