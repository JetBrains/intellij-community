// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinHighlighterAttributeUtils")

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

@ApiStatus.Internal
fun textAttributesKeyForKtElement(element: PsiElement): HighlightInfoType? {
    return textAttributesKeyForTypeDeclaration(element) ?:
    textAttributesKeyForKtFunction(element) ?:
    textAttributesKeyForPropertyDeclaration(element)
}

@ApiStatus.Internal
fun textAttributesKeyForPropertyDeclaration(declaration: PsiElement): HighlightInfoType? = when (declaration) {
    is KtProperty -> textAttributesForKtPropertyDeclaration(declaration)
    is KtParameter -> textAttributesForKtParameterDeclaration(declaration)
    is PsiLocalVariable -> KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE
    is PsiParameter -> KotlinHighlightInfoTypeSemanticNames.PARAMETER
    is PsiField -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
    else -> null
}

@ApiStatus.Internal
fun textAttributesForKtParameterDeclaration(parameter: KtParameter): HighlightInfoType = when {
    parameter.valOrVarKeyword != null -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
    parameter.parent?.takeIf { it is KtForExpression || it.parent is KtCatchClause } != null -> KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE
    else -> KotlinHighlightInfoTypeSemanticNames.PARAMETER
}

@ApiStatus.Internal
fun textAttributesForKtPropertyDeclaration(property: KtProperty): HighlightInfoType = when {
    property.isExtensionDeclaration() -> KotlinHighlightInfoTypeSemanticNames.EXTENSION_PROPERTY
    property.isLocal -> KotlinHighlightInfoTypeSemanticNames.LOCAL_VARIABLE
    property.isTopLevel -> when {
        property.isCustomPropertyDeclaration() -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_PROPERTY
    }
    else -> when {
        property.isCustomPropertyDeclaration() -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION
        else -> KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
    }
}

@ApiStatus.Internal
fun textAttributesKeyForKtFunction(function: PsiElement): HighlightInfoType? = when (function) {
    is KtFunction -> KotlinHighlightInfoTypeSemanticNames.FUNCTION_DECLARATION
    else -> null
}

@ApiStatus.Internal
fun textAttributesKeyForTypeDeclaration(declaration: PsiElement): HighlightInfoType? = when {
    declaration is KtTypeParameter || declaration is PsiTypeParameter -> KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER
    declaration is KtTypeAlias -> KotlinHighlightInfoTypeSemanticNames.TYPE_ALIAS
    declaration is KtClass -> textAttributesForClass(declaration)
    declaration is PsiClass && declaration.isInterface && !declaration.isAnnotationType -> KotlinHighlightInfoTypeSemanticNames.TRAIT
    declaration is KtPrimaryConstructor && declaration.containingClassOrObject?.isAnnotation() == true -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
    declaration is KtObjectDeclaration -> when {
        declaration.isData() -> KotlinHighlightInfoTypeSemanticNames.DATA_OBJECT
        else -> KotlinHighlightInfoTypeSemanticNames.OBJECT
    }
    declaration is PsiEnumConstant -> KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY
    declaration is PsiClass -> when {
        declaration.isAnnotationType -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
        declaration.hasModifier(JvmModifier.ABSTRACT) -> KotlinHighlightInfoTypeSemanticNames.ABSTRACT_CLASS
        else -> KotlinHighlightInfoTypeSemanticNames.CLASS
    }
    else -> null
}

@ApiStatus.Internal
fun textAttributesForClass(klass: KtClass): HighlightInfoType = when {
    klass.isInterface() -> KotlinHighlightInfoTypeSemanticNames.TRAIT
    klass.isAnnotation() -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
    klass.isEnum() -> KotlinHighlightInfoTypeSemanticNames.ENUM
    klass is KtEnumEntry -> KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY
    klass.isAbstract() -> KotlinHighlightInfoTypeSemanticNames.ABSTRACT_CLASS
    klass.isData() -> KotlinHighlightInfoTypeSemanticNames.DATA_CLASS
    else -> KotlinHighlightInfoTypeSemanticNames.CLASS
}

private fun KtProperty.isCustomPropertyDeclaration(): Boolean {
    return getter?.bodyExpression != null || setter?.bodyExpression != null
}