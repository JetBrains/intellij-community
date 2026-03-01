// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDynamicType
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.util.unwrapIfTypeAlias
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class TypeKindHighlightingVisitor(holder: HighlightInfoHolder, bindingContext: BindingContext) :
    AfterAnalysisHighlightingVisitor(holder, bindingContext) {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val parent = expression.parent
        if (parent is KtSuperExpression || parent is KtThisExpression) {
            // Do nothing: 'super' and 'this' are highlighted as a keyword
            return
        }

        // Prevent custom highlighting for a name that is a part of a definitely non-nullable type
        // (the only kind of intersection types that is currently supported by the compiler).
        // The type is highlighted as a whole in `visitIntersectionType`.
        if (parent?.parent?.parent?.safeAs<KtIntersectionType>() != null) return

        val referenceTarget = computeReferencedDescriptor(expression) ?: return
        val textRange = computeHighlightingRangeForUsage(expression, referenceTarget)
        val key = attributeKeyForObjectAccess(expression) ?: calculateDeclarationReferenceAttributes(referenceTarget) ?: return
        if (key != KotlinHighlightInfoTypeSemanticNames.ANNOTATION
            || parent?.parentOfTypes(KtImportDirective::class, KtPackageDirective::class, KtTypeAlias::class) != null) { // annotation was highlighted in AnnoEntryHighVisitor
            val parentAnno = PsiTreeUtil.getParentOfType(
                expression, KtAnnotationEntry::class.java, /* strict = */false, KtValueArgumentList::class.java
            )
            val toHighlight = parentAnno ?: expression
            if (toHighlight !is KtAnnotationEntry) {
                highlightName(expression.project, toHighlight, textRange, key)
            }
        }
    }

    private fun attributeKeyForObjectAccess(expression: KtSimpleNameExpression): HighlightInfoType? {
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
        val target = referenceTarget.unwrapIfTypeAlias() as? ClassDescriptor
        if (target?.kind != ClassKind.ANNOTATION_CLASS) return expression.textRange

        // include '@' symbol if the reference is the first segment of KtAnnotationEntry
        // if "Deprecated" is highlighted then '@' should be highlighted too in "@Deprecated"
        val annotationEntry = PsiTreeUtil.getParentOfType(
            expression, KtAnnotationEntry::class.java, /* strict = */false, KtValueArgumentList::class.java
        )
        val atSymbol = annotationEntry?.atSymbol ?: return expression.textRange
        return TextRange(atSymbol.textRange.startOffset, expression.textRange.endOffset)
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        val identifier = classOrObject.nameIdentifier
        val classDescriptor = bindingContext.get(BindingContext.CLASS, classOrObject)
        if (identifier != null
            && classDescriptor != null && identifier.parent !is KtClass
            && classOrObject !is KtObjectDeclaration
            ) { // class was highlighted in DeclarationHighlightingVisitor
            highlightName(
                identifier,
                attributeKeyForDeclarationFromExtensions(classOrObject, classDescriptor)
                    ?: calculateClassReferenceAttributes(classDescriptor)
            )
        }
        super.visitClassOrObject(classOrObject)
    }

    override fun visitDynamicType(type: KtDynamicType) {
        // Do nothing: 'dynamic' is highlighted as a keyword
    }

    override fun visitIntersectionType(type: KtIntersectionType) {
        // Currently, the only kind of intersection types is definitely non-nullable type, so highlight it without further analysis
        type.parent?.safeAs<KtTypeReference>()?.run { highlightName(this, KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER) }
        super.visitIntersectionType(type)
    }

    private fun calculateClassReferenceAttributes(target: ClassDescriptor): HighlightInfoType {
        return when (target.kind) {
            ClassKind.ANNOTATION_CLASS -> KotlinHighlightInfoTypeSemanticNames.ANNOTATION
            ClassKind.INTERFACE -> KotlinHighlightInfoTypeSemanticNames.TRAIT
            ClassKind.OBJECT -> if (target.isData) {
                KotlinHighlightInfoTypeSemanticNames.DATA_OBJECT
            } else {
                KotlinHighlightInfoTypeSemanticNames.OBJECT
            }
            ClassKind.ENUM_CLASS -> KotlinHighlightInfoTypeSemanticNames.ENUM
            ClassKind.ENUM_ENTRY -> KotlinHighlightInfoTypeSemanticNames.ENUM_ENTRY
            else -> if (target.modality === Modality.ABSTRACT) {
                KotlinHighlightInfoTypeSemanticNames.ABSTRACT_CLASS
            } else if (target.isData) {
                KotlinHighlightInfoTypeSemanticNames.DATA_CLASS
            } else {
                KotlinHighlightInfoTypeSemanticNames.CLASS
            }
        }
    }

    private fun calculateTypeAliasReferenceAttributes(target: TypeAliasDescriptor): HighlightInfoType {
        val aliasedTarget = target.expandedType.constructor.declarationDescriptor
        return if (aliasedTarget is ClassDescriptor && aliasedTarget.kind == ClassKind.ANNOTATION_CLASS) KotlinHighlightInfoTypeSemanticNames.ANNOTATION else KotlinHighlightInfoTypeSemanticNames.TYPE_ALIAS
    }

    private fun calculateDeclarationReferenceAttributes(target: DeclarationDescriptor): HighlightInfoType? {
        return when (target) {
            is TypeParameterDescriptor -> KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER
            is TypeAliasDescriptor -> calculateTypeAliasReferenceAttributes(target)
            is ClassDescriptor -> calculateClassReferenceAttributes(target)
            else -> null
        }
    }
}
