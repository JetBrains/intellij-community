// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix.Kind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render


class OptInFixes {
    /**
     * A specialized subclass of [AddAnnotationFix] that adds @OptIn(...) annotations to declarations,
     * containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getPresentation] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param optInClassId fully qualified opt-in class id
     * @param kind the annotation kind (desired scope)
     * @param argumentClassFqName the fully qualified name of the annotation to opt-in
     * @param priority wanted priority of the action
     *
     */
    class UseOptInAnnotationFix(
        element: KtElement,
        private val optInClassId: ClassId,
        private val kind: Kind,
        private val argumentClassFqName: FqName,
        private val priority: PriorityAction.Priority,
    ) : AddAnnotationFix(element, optInClassId, Kind.Self, argumentClassFqName.asClassLiteral()) {

        public override fun getPresentation(context: ActionContext, element: KtElement): Presentation =
            getOptInAnnotationFixPresentation(element, kind, argumentClassFqName).withPriority(priority)

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }

    class ModifyOptInAnnotationFix(
        element: KtAnnotationEntry,
        private val kind: Kind,
        private val argumentClassFqName: FqName,
        private val priority: PriorityAction.Priority
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

        public override fun getPresentation(context: ActionContext, element: KtAnnotationEntry): Presentation =
            getOptInAnnotationFixPresentation(element, kind, argumentClassFqName).withPriority(priority)

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")

        override fun invoke(context: ActionContext, element: KtAnnotationEntry, updater: ModPsiUpdater) {
            val annotationInnerText = argumentClassFqName.asClassLiteral()
            val psiFactory = KtPsiFactory(element.project)
            element.valueArgumentList?.addArgument(psiFactory.createArgument(annotationInnerText))
                ?: element.addAfter(psiFactory.createCallArguments("($annotationInnerText)"), element.lastChild)
            ShortenReferencesFacility.getInstance().shorten(element)
        }
    }

    /**
     * A specialized subclass of [AddAnnotationFix] that adds propagating opted-in annotations
     * to declarations, containing classes, or constructors.
     *
     * This class reuses the parent's [invoke] method but overrides the [getPresentation] method to provide
     * more descriptive opt-in-specific messages.
     *
     * @param element a declaration to annotate
     * @param annotationClassId fully qualified annotation class id
     * @param kind the annotation kind (desired scope)
     * @param argumentClassFqName the qualified class name to be added to the annotation entry in the format '::class'
     * @param priority wanted priority of the action
     *
     */
    class PropagateOptInAnnotationFix(
        element: KtDeclaration,
        private val annotationClassId: ClassId,
        private val kind: Kind,
        private val argumentClassFqName: FqName? = null,
        private val priority: PriorityAction.Priority = PriorityAction.Priority.NORMAL,
    ) : AddAnnotationFix(element, annotationClassId, Kind.Self, argumentClassFqName?.asClassLiteral()) {

        override fun getPresentation(context: ActionContext, element: KtElement): Presentation {
            val annotationName = annotationClassId.shortClassName.asString()
            val annotationEntry = if (argumentClassFqName != null) "(${argumentClassFqName.shortName().asString()}::class)" else ""
            val argumentText = annotationName + annotationEntry
            val actionName = when {
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
            return Presentation.of(actionName).withPriority(priority)
        }

        override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.annotation.family")
    }
}

private fun FqName.asClassLiteral(): String = "${render()}::class"

private fun getOptInAnnotationFixPresentation(
    element: KtElement,
    kind: Kind,
    argumentClassFqName: FqName
): Presentation {
    val argumentText = argumentClassFqName.shortName().asString()
    val actionName = when {
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

        kind is Kind.ContainingClass -> KotlinBundle.message(
            "fix.opt_in.text.use.containing.class",
            argumentText,
            kind.name ?: "?"
        )

        else -> error("Unexpected kind type")
    }
    return Presentation.of(actionName)
}
