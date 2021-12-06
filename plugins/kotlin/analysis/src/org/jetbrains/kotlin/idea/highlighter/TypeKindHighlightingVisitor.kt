// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors.*
import org.jetbrains.kotlin.idea.project.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.types.DefinitelyNotNullType

internal class TypeKindHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val parent = expression.parent
        if (parent is KtSuperExpression || parent is KtThisExpression) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return
        }

        val intersectionType = expression.getParentOfType<KtIntersectionType>(strict = true)
        val resolvedType = bindingContext[BindingContext.TYPE, intersectionType?.parent as? KtTypeReference]
        if (intersectionType != null && resolvedType is DefinitelyNotNullType) {
            // Do nothing: this name is a component of a definitely non-nullable type
            // that will be highlighted as a whole in `visitIntersectionType`
            return
        }

        if (!NameHighlighter.namesHighlightingEnabled) return

        val referenceTarget = computeReferencedDescriptor(expression) ?: return
        val key = attributeKeyForObjectAccess(expression) ?: calculateDeclarationReferenceAttributes(referenceTarget) ?: return
        highlightName(computeHighlightingRangeForUsage(expression, referenceTarget), key)
    }

    private fun attributeKeyForObjectAccess(expression: KtSimpleNameExpression): TextAttributesKey? {
        val resolvedCall = expression.getResolvedCall(bindingContext)
        return if (resolvedCall?.resultingDescriptor is FakeCallableDescriptorForObject)
            attributeKeyForCallFromExtensions(expression, resolvedCall)
        else null
    }

    private fun computeReferencedDescriptor(expression: KtSimpleNameExpression): DeclarationDescriptor? {
        val referenceTarget = bindingContext.get(BindingContext.REFERENCE_TARGET, expression)

        if (referenceTarget !is ConstructorDescriptor) return referenceTarget

        val callElement = expression.getParentOfTypeAndBranch<KtCallExpression>(true) { calleeExpression }
            ?: expression.getParentOfTypeAndBranch<KtSuperTypeCallEntry>(true) { calleeExpression }

        if (callElement != null) {
            return referenceTarget
        }

        return referenceTarget.containingDeclaration
    }


    private fun computeHighlightingRangeForUsage(expression: KtSimpleNameExpression, referenceTarget: DeclarationDescriptor): TextRange {
        val expressionRange = expression.textRange

        if (referenceTarget !is ClassDescriptor || referenceTarget.kind != ClassKind.ANNOTATION_CLASS) return expressionRange

        // include '@' symbol if the reference is the first segment of KtAnnotationEntry
        // if "Deprecated" is highlighted then '@' should be highlighted too in "@Deprecated"
        val annotationEntry = PsiTreeUtil.getParentOfType(
            expression, KtAnnotationEntry::class.java, /* strict = */false, KtValueArgumentList::class.java
        )
        val atSymbol = annotationEntry?.atSymbol ?: return expressionRange
        return TextRange(atSymbol.textRange.startOffset, expression.textRange.endOffset)
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        val identifier = classOrObject.nameIdentifier
        val classDescriptor = bindingContext.get(BindingContext.CLASS, classOrObject)
        if (identifier != null && classDescriptor != null) {
            highlightName(
                identifier,
                attributeKeyForDeclarationFromExtensions(classOrObject, classDescriptor)
                    ?: calculateClassReferenceAttributes(classDescriptor)
            )
        }
        super.visitClassOrObject(classOrObject)
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        val identifier = typeAlias.nameIdentifier
        val descriptor = bindingContext.get(BindingContext.TYPE_ALIAS, typeAlias)
        if (identifier != null && descriptor != null) {
            val key = attributeKeyForDeclarationFromExtensions(identifier, descriptor)
                ?: calculateTypeAliasReferenceAttributes(descriptor)

            highlightName(identifier, key)
        }
        super.visitTypeAlias(typeAlias)
    }

    override fun visitDynamicType(type: KtDynamicType) {
        // Do nothing: 'dynamic' is highlighted as a keyword
    }

    override fun visitIntersectionType(type: KtIntersectionType) {
        val parent = type.parent as? KtTypeReference
        val kotlinType = bindingContext[BindingContext.TYPE, parent]
        if (parent != null && kotlinType is DefinitelyNotNullType) {
            highlightName(parent, TYPE_PARAMETER)
        }
        super.visitIntersectionType(type)
    }

    private fun calculateClassReferenceAttributes(target: ClassDescriptor): TextAttributesKey {
        return when (target.kind) {
            ClassKind.ANNOTATION_CLASS -> ANNOTATION
            ClassKind.INTERFACE -> TRAIT
            ClassKind.OBJECT -> OBJECT
            ClassKind.ENUM_CLASS -> ENUM
            ClassKind.ENUM_ENTRY -> ENUM_ENTRY
            else -> if (target.modality === Modality.ABSTRACT) ABSTRACT_CLASS else CLASS
        }
    }

    private fun calculateTypeAliasReferenceAttributes(target: TypeAliasDescriptor): TextAttributesKey {
        val aliasedTarget = target.expandedType.constructor.declarationDescriptor
        return if (aliasedTarget is ClassDescriptor && aliasedTarget.kind == ClassKind.ANNOTATION_CLASS) ANNOTATION else TYPE_ALIAS
    }

    private fun calculateDeclarationReferenceAttributes(target: DeclarationDescriptor): TextAttributesKey? {
        return when (target) {
            is TypeParameterDescriptor -> TYPE_PARAMETER
            is TypeAliasDescriptor -> calculateTypeAliasReferenceAttributes(target)
            is ClassDescriptor -> calculateClassReferenceAttributes(target)
            else -> null
        }
    }
}
