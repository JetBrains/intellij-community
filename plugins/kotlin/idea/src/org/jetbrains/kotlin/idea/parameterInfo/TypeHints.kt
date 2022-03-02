// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetails
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.core.util.isMultiLine
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.idea.refactoring.getLineNumber
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.containsError
import org.jetbrains.kotlin.types.typeUtil.immediateSupertypes
import org.jetbrains.kotlin.types.typeUtil.isEnum
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun providePropertyTypeHint(elem: PsiElement): List<InlayInfoDetails> {
    (elem as? KtCallableDeclaration)?.let { property ->
        property.nameIdentifier?.let { ident ->
            provideTypeHint(property, ident.endOffset)?.let { return listOf(it) }
        }
    }
    return emptyList()
}

fun provideTypeHint(element: KtCallableDeclaration, offset: Int): InlayInfoDetails? {
    var type: KotlinType = SpecifyTypeExplicitlyIntention.getTypeForDeclaration(element).unwrap()
    if (type.containsError()) return null
    val declarationDescriptor = type.constructor.declarationDescriptor
    val name = declarationDescriptor?.name
    if (name == SpecialNames.NO_NAME_PROVIDED) {
        if (element is KtProperty && element.isLocal) {
            // for local variables, an anonymous object type is not collapsed to its supertype,
            // so showing the supertype will be misleading
            return null
        }
        type = type.immediateSupertypes().singleOrNull() ?: return null
    } else if (name?.isSpecial == true) {
        return null
    }

    if (element is KtProperty && element.isLocal && type.isUnit() && element.isMultiLine()) {
        val propertyLine = element.getLineNumber()
        val equalsTokenLine = element.equalsToken?.getLineNumber() ?: -1
        val initializerLine = element.initializer?.getLineNumber() ?: -1
        if (propertyLine == equalsTokenLine && propertyLine != initializerLine) {
            val indentBeforeProperty = (element.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n')
            val indentBeforeInitializer = (element.initializer?.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n')
            if (indentBeforeProperty == indentBeforeInitializer) {
                return null
            }
        }
    }

    return if (isUnclearType(type, element)) {
        val settings = element.containingKtFile.kotlinCustomSettings
        val renderedType = HintsTypeRenderer.getInlayHintsTypeRenderer(element.safeAnalyzeNonSourceRootCode(), element).renderTypeIntoInlayInfo(type)
        val prefix = buildString {
            if (settings.SPACE_BEFORE_TYPE_COLON) {
                append(" ")
            }

            append(":")
            if (settings.SPACE_AFTER_TYPE_COLON) {
                append(" ")
            }
        }

        val inlayInfo = InlayInfo(
            text = "", offset = offset,
            isShowOnlyIfExistedBefore = false, isFilterByBlacklist = true, relatesToPrecedingText = true
        )
        return InlayInfoDetails(inlayInfo, listOf(TextInlayInfoDetail(prefix)) + renderedType)
    } else {
        null
    }
}

private fun isUnclearType(type: KotlinType, element: KtCallableDeclaration): Boolean {
    if (element !is KtProperty) return true

    val initializer = element.initializer ?: return true
    if (initializer is KtConstantExpression || initializer is KtStringTemplateExpression) return false
    if (initializer is KtUnaryExpression && initializer.baseExpression is KtConstantExpression) return false

    if (isConstructorCall(initializer)) {
        return false
    }

    if (initializer is KtDotQualifiedExpression) {
        val selectorExpression = initializer.selectorExpression
        if (type.isEnum()) {
            // Do not show type for enums if initializer has enum entry with explicit enum name: val p = Enum.ENTRY
            val enumEntryDescriptor: DeclarationDescriptor? = selectorExpression?.resolveMainReferenceToDescriptors()?.singleOrNull()

            if (enumEntryDescriptor != null && DescriptorUtils.isEnumEntry(enumEntryDescriptor)) {
                return false
            }
        }

        if (initializer.receiverExpression.isClassOrPackageReference() && isConstructorCall(selectorExpression)) {
            return false
        }
    }

    return true
}

private fun isConstructorCall(initializer: KtExpression?): Boolean {
    if (initializer is KtCallExpression) {
        val resolvedCall = initializer.resolveToCall(BodyResolveMode.FULL)
        val resolvedDescriptor = resolvedCall?.candidateDescriptor
        if (resolvedDescriptor is SamConstructorDescriptor) {
            return true
        }
        if (resolvedDescriptor is ConstructorDescriptor &&
            (resolvedDescriptor.constructedClass.declaredTypeParameters.isEmpty() || initializer.typeArgumentList != null)
        ) {
            return true
        }
        return false
    }

    return false
}

private fun KtExpression.isClassOrPackageReference(): Boolean =
    when (this) {
        is KtNameReferenceExpression -> this.resolveMainReferenceToDescriptors().singleOrNull()
            .let { it is ClassDescriptor || it is PackageViewDescriptor }
        is KtDotQualifiedExpression -> this.selectorExpression?.isClassOrPackageReference() ?: false
        else -> false
    }
