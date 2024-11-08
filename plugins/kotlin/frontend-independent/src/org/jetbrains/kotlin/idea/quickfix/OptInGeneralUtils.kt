// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.resolve.checkers.OptInNames

// TODO: migrate from FqName to ClassId fully when the K1 plugin is dropped.
abstract class OptInGeneralUtilsBase {
    data class CandidateData(val element: KtElement, val kind: AddAnnotationFix.Kind)

    abstract fun KtDeclaration.isSubclassOptPropagateApplicable(annotationFqName: FqName): Boolean

    fun collectPropagateOptInAnnotationFix(
        targetElement: KtDeclaration,
        kind: AddAnnotationFix.Kind,
        applicableTargets: Set<KotlinTarget>,
        actualTargetList: List<KotlinTarget>,
        annotationClassId: ClassId,
        isOverrideError: Boolean
    ): AddAnnotationFix? {
        if (KtPsiUtil.isLocal(targetElement)) return null
        if (actualTargetList.none { it in applicableTargets }) return null

        val annotationFqName = annotationClassId.asSingleFqName()
        return when {
            // When we are fixing a missing annotation on an overridden function, we should
            // propose to add a propagating annotation first, and in all other cases
            // the non-propagating opt-in annotation should be default.
            // The same logic applies to the similar conditional expressions onward.
            isOverrideError -> OptInFixes.HighPriorityPropagateOptInAnnotationFix(targetElement, annotationClassId, kind)

            targetElement.isSubclassOptPropagateApplicable(annotationFqName) -> OptInFixes.PropagateOptInAnnotationFix(
                targetElement, ClassId.topLevel(OptInNames.SUBCLASS_OPT_IN_REQUIRED_FQ_NAME), kind, annotationFqName
            )

            else -> OptInFixes.PropagateOptInAnnotationFix(targetElement, annotationClassId, kind)
        }
    }

    fun collectUseOptInAnnotationFix(
        targetElement: KtElement,
        kind: AddAnnotationFix.Kind,
        optInClassId: ClassId,
        annotationFqName: FqName,
        isOverrideError: Boolean
    ): AddAnnotationFix {
        val existingAnnotationEntry = (targetElement as? KtAnnotated)?.findAnnotation(optInClassId)?.createSmartPointer()

        return if (isOverrideError) {
            OptInFixes.UseOptInAnnotationFix(targetElement, optInClassId, kind, annotationFqName, existingAnnotationEntry)
        } else {
            OptInFixes.HighPriorityUseOptInAnnotationFix(targetElement, optInClassId, kind, annotationFqName, existingAnnotationEntry)
        }

    }

    fun collectCandidates(element: PsiElement): List<CandidateData> {
        val result = mutableListOf<CandidateData>()

        val containingDeclaration: KtDeclaration = element.getParentOfTypesAndPredicate(
            strict = false,
            KtDeclarationWithBody::class.java,
            KtClassOrObject::class.java,
            KtProperty::class.java,
            KtTypeAlias::class.java
        ) {
            !KtPsiUtil.isLocal(it)
        } ?: return emptyList()

        val containingDeclarationCandidate = findContainingDeclarationCandidate(containingDeclaration)
        result.add(containingDeclarationCandidate)
        if (containingDeclaration is KtCallableDeclaration) {
            findContainingClassOrObjectCandidate(containingDeclaration)?.let(result::add)
        }

        return result
    }


    fun findContainingDeclarationCandidate(element: KtDeclaration): CandidateData {
        val kind = if (element is KtConstructor<*>) AddAnnotationFix.Kind.Constructor
        else AddAnnotationFix.Kind.Declaration(element.name)

        return CandidateData(element, kind)
    }

    fun findContainingClassOrObjectCandidate(element: KtDeclaration): CandidateData? {
        val containingClassOrObject = element as? KtClassOrObject ?: (element.containingClassOrObject ?: return null)
        return CandidateData(containingClassOrObject, AddAnnotationFix.Kind.ContainingClass(containingClassOrObject.name))
    }
}