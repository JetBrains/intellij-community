// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.base.fe10.analysis.classId
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Quick fix factory for forbidden OptIn annotation usage.
 *
 * Annotations that indicate the opt-in requirement are forbidden in several use sites:
 * getters, value parameters, local variables. This factory generates quick fixes
 * to replace it with an allowed annotation variant
 * (e.g., annotate a property instead of a getter or a value parameter).
 */
object OptInAnnotationWrongTargetFixesFactory : KotlinIntentionActionsFactory() {
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
        val annotationClassId = bindingContext[BindingContext.ANNOTATION, annotationEntry]?.classId
        if (annotationClassId != null) {
            when {
                annotatedElement is KtParameter && annotationUseSiteTarget != AnnotationUseSiteTarget.PROPERTY ->
                    result.add(
                        MoveOptInRequirementToPropertyFix(
                            MoveOptInRequirementToPropertyFix.SourceType.VALUE_PARAMETER,
                            annotationEntry,
                            annotatedElement.createSmartPointer(),
                            annotationClassId,
                            AnnotationUseSiteTarget.PROPERTY
                        )
                    )

                annotatedElement is KtProperty
                        && (annotatedElement.hasCustomGetter() || annotationUseSiteTarget == AnnotationUseSiteTarget.PROPERTY_GETTER) ->
                    result.add(
                        MoveOptInRequirementToPropertyFix(
                            MoveOptInRequirementToPropertyFix.SourceType.GETTER,
                            annotationEntry,
                            annotatedElement.createSmartPointer(),
                            annotationClassId
                        )
                    )
            }
        }

        return result
    }
}


/**
 * Specialized version of [ReplaceAnnotationFix] for moving opt-in propagating
 * annotations from value parameters of constructors or getter to corresponding properties.
 *
 * @param type the annotation entry to move
 * @param annotationEntry the annotation entry to move
 * @param modifierListOwner the property whose getter is currently annotated
 * @param annotationClassId fully qualified annotation class id
 * @param useSiteTarget the use site target of the annotation, or null if no explicit use site target is provided
 * @param existingReplacementAnnotationEntry the existing annotation to update (null by default)
 */
class MoveOptInRequirementToPropertyFix(
    val type: SourceType,
    annotationEntry: KtAnnotationEntry,
    modifierListOwner: SmartPsiElementPointer<KtModifierListOwner>,
    annotationClassId: ClassId,
    useSiteTarget: AnnotationUseSiteTarget? = null,
    existingReplacementAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null,
) : ReplaceAnnotationFix(
    annotationEntry,
    modifierListOwner,
    annotationClassId,
    argumentClassFqName = null,
    useSiteTarget,
    existingReplacementAnnotationEntry = existingReplacementAnnotationEntry
), HighPriorityAction {

    enum class SourceType {
        VALUE_PARAMETER,
        GETTER
    }

    override fun getText(): String {
        val annotationText = renderAnnotationText(renderUserSiteTarget = false)
        return when (type) {
            SourceType.VALUE_PARAMETER -> KotlinBundle.message(
                "fix.opt_in.move.requirement.from.value.parameter.to.property",
                annotationText
            )

            SourceType.GETTER -> KotlinBundle.message("fix.opt_in.move.requirement.from.getter.to.property", annotationText)
        }
    }
}

