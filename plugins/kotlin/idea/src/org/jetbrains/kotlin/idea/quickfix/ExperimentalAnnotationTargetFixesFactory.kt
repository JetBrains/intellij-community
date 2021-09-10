// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Quick fix factory for forbidden experimental annotation usage.
 *
 * Annotations that indicate the opt-in requirement are forbidden in several use sites:
 * getters, value parameters, local variables. This factory generates quick fixes
 * to either remove the annotation or to replace it with an allowed annotation variant
 * (e.g., annotate a property instead of a getter or a value parameter).
 */
object ExperimentalAnnotationWrongTargetFixesFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        if (diagnostic.factory != Errors.OPT_IN_MARKER_ON_WRONG_TARGET) return emptyList()
        val annotationEntry = diagnostic.psiElement.safeAs<KtAnnotationEntry>() ?: return emptyList()
        val annotationUseSiteTarget = annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget()
        val annotatedElement = annotationEntry.getParentOfTypes(
            strict = true,
            KtProperty::class.java,
            KtParameter::class.java,
        ) ?: return emptyList()

        val result = mutableListOf<IntentionAction>()
        val bindingContext = annotationEntry.analyze(BodyResolveMode.PARTIAL)
        val annotationFqName = bindingContext[BindingContext.ANNOTATION, annotationEntry]?.fqName
        if (annotationFqName != null) {
            when {
                annotatedElement is KtParameter && annotationUseSiteTarget != AnnotationUseSiteTarget.PROPERTY ->
                    result.add(
                        MoveOptInRequirementFromValueParameterToPropertyFix(
                            annotationEntry,
                            annotatedElement.createSmartPointer(),
                            annotationFqName
                        )
                    )

                annotatedElement is KtProperty
                        && (annotatedElement.hasCustomGetter() || annotationUseSiteTarget == AnnotationUseSiteTarget.PROPERTY_GETTER) ->
                    result.add(
                        MoveOptInRequirementFromGetterToPropertyFix(
                            annotationEntry,
                            annotatedElement.createSmartPointer(),
                            annotationFqName
                        )
                    )
            }
        }

        // 'Remove annotation' action is the universal fallback
        result.addAll(RemoveAnnotationFix.createActions(diagnostic))
        return result
    }
}

/**
 * High priority, specialized version of [ReplaceAnnotationFix] for moving opt-in propagating
 * annotations from property getters to corresponding properties.
 *
 * @param annotationEntry the annotation entry to move
 * @param modifierListOwner the property whose getter is currently annotated
 * @param annotationFqName fully qualified annotation class name
 * @param existingReplacementAnnotationEntry the existing annotation to update (null by default)
 */
private class MoveOptInRequirementFromGetterToPropertyFix(
    annotationEntry: KtAnnotationEntry,
    modifierListOwner: SmartPsiElementPointer<KtModifierListOwner>,
    annotationFqName: FqName,
    existingReplacementAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : ReplaceAnnotationFix(
    annotationEntry,
    modifierListOwner,
    annotationFqName,
    argumentClassFqName = null,
    useSiteTarget = null,
    existingReplacementAnnotationEntry = existingReplacementAnnotationEntry
), HighPriorityAction {
    override fun getText(): String {
        return KotlinBundle.message(
            "fix.opt_in.move.requirement.from.getter.to.property",
            renderAnnotationText(renderUserSiteTarget = false))
    }
}

/**
 * High priority, specialized version of [ReplaceAnnotationFix] for moving opt-in propagating
 * annotations from value parameters of constructors to corresponding properties.
 *
 * @param annotationEntry the annotation entry to move
 * @param modifierListOwner the property whose getter is currently annotated
 * @param annotationFqName fully qualified annotation class name
 * @param existingReplacementAnnotationEntry the existing annotation to update (null by default)
 */
private class MoveOptInRequirementFromValueParameterToPropertyFix(
    annotationEntry: KtAnnotationEntry,
    modifierListOwner: SmartPsiElementPointer<KtModifierListOwner>,
    annotationFqName: FqName,
    existingReplacementAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
) : ReplaceAnnotationFix(
    annotationEntry,
    modifierListOwner,
    annotationFqName,
    argumentClassFqName = null,
    useSiteTarget = AnnotationUseSiteTarget.PROPERTY,
    existingReplacementAnnotationEntry = existingReplacementAnnotationEntry
), HighPriorityAction {
    override fun getText(): String {
        return KotlinBundle.message(
            "fix.opt_in.move.requirement.from.value.parameter.to.property",
            renderAnnotationText(renderUserSiteTarget = false))
    }
}
