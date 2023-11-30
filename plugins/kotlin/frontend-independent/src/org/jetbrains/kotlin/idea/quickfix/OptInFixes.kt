// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*


class OptInFixes {
    /**
     * A specialized subclass of [AddAnnotationFix] that adds @OptIn(...) annotations to declarations,
     * containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getText] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param optInClassId fully qualified opt-in class id
     * @param kind the annotation kind (desired scope)
     * @param argumentClassFqName the fully qualified name of the annotation to opt-in
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     *
     */
    open class UseOptInAnnotationFix(
        element: KtElement,
        optInClassId: ClassId,
        private val kind: Kind,
        private val argumentClassFqName: FqName,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, optInClassId, kind, argumentClassFqName, existingAnnotationEntry) {

        override fun getText(): String {
            val argumentText = argumentClassFqName.shortName().asString()
            return when {
                kind is Kind.Self -> KotlinBundle.message("fix.opt_in.text.use.statement", argumentText)
                kind is Kind.Constructor -> KotlinBundle.message("fix.opt_in.text.use.constructor", argumentText)
                kind is Kind.Declaration -> KotlinBundle.message("fix.opt_in.text.use.declaration", argumentText, kind.name ?: "?")
                kind is Kind.ContainingClass && element is KtObjectDeclaration && kind.name != null -> KotlinBundle.message(
                    "fix.opt_in.text.use.containing.object",
                    argumentText,
                    kind.name
                )

                kind is Kind.ContainingClass && element is KtObjectDeclaration && kind.name == null -> KotlinBundle.message(
                    "fix.opt_in.text.use.containing.anonymous.object",
                    argumentText
                )

                kind is Kind.ContainingClass -> KotlinBundle.message("fix.opt_in.text.use.containing.class", argumentText, kind.name ?: "?")
                else -> error("Unexpected kind type")
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    class HighPriorityUseOptInAnnotationFix(
        element: KtElement,
        optInClassId: ClassId,
        kind: Kind,
        argumentClassFqName: FqName,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : UseOptInAnnotationFix(element, optInClassId, kind, argumentClassFqName, existingAnnotationEntry), HighPriorityAction


    /**
     * A specialized subclass of [AddAnnotationFix] that adds propagating opted-in annotations
     * to declarations, containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getText] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param annotationClassId fully qualified annotation class id
     * @param kind the annotation kind (desired scope)
     * @param argumentClassFqName the qualified class name to be added to the annotation entry in the format '::class'
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     *
     */
    open class PropagateOptInAnnotationFix(
        element: KtDeclaration,
        private val annotationClassId: ClassId,
        private val kind: Kind,
        private val argumentClassFqName: FqName? = null,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : AddAnnotationFix(element, annotationClassId, Kind.Self, argumentClassFqName, existingAnnotationEntry) {

        override fun getText(): String {
            val annotationName = annotationClassId.shortClassName.asString()
            val annotationEntry = if (argumentClassFqName != null) "(${argumentClassFqName.shortName().asString()}::class)" else ""
            val argumentText = annotationName + annotationEntry
            return when {
                kind is Kind.Self -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, "?")
                kind is Kind.Constructor -> KotlinBundle.message("fix.opt_in.text.propagate.constructor", argumentText)
                kind is Kind.Declaration -> KotlinBundle.message("fix.opt_in.text.propagate.declaration", argumentText, kind.name ?: "?")
                kind is Kind.ContainingClass && element is KtObjectDeclaration -> KotlinBundle.message(
                    "fix.opt_in.text.propagate.containing.object", argumentText, kind.name ?: "?"
                )

                kind is Kind.ContainingClass -> KotlinBundle.message(
                    "fix.opt_in.text.propagate.containing.class", argumentText, kind.name ?: "?"
                )

                else -> error("Unexpected kind type")
            }
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    /**
     * A high-priority version of [PropagateOptInAnnotationFix] (for overridden constructor case)
     *
     * @param element a declaration to annotate
     * @param annotationClassId fully qualified annotation class id
     * @param kind the annotation kind (desired scope)
     * @param existingAnnotationEntry the already existing annotation entry (if any)
     */
    class HighPriorityPropagateOptInAnnotationFix(
        element: KtDeclaration,
        annotationClassId: ClassId,
        kind: Kind,
        existingAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ) : PropagateOptInAnnotationFix(element, annotationClassId, kind, null, existingAnnotationEntry), HighPriorityAction
}