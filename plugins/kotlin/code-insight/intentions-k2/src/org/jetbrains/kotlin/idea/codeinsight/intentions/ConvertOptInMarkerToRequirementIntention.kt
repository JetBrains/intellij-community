// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal class ConvertOptInMarkerToRequirementIntention :
    KotlinApplicableModCommandAction<KtValueArgument, ConvertOptInMarkerToRequirementIntention.Context>(
        KtValueArgument::class
    ) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtValueArgument,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)

        val optInAnnotation = element.parentOfType<KtAnnotationEntry>() ?: return
        if (optInAnnotation.parent is KtExpression) return // Converting an @OptIn marker on an expression to a requirement doesn't make sense

        val useSiteTarget = optInAnnotation.useSiteTarget

        val toAdd = when (useSiteTarget?.getAnnotationUseSiteTarget()) {
            AnnotationUseSiteTarget.FILE -> {
                psiFactory.createFileAnnotation(elementContext.classId.asFqNameString())
            }

            else -> {
                val useSiteTargetRendered = useSiteTarget?.getAnnotationUseSiteTarget()?.renderName?.let { "$it:" } ?: ""
                psiFactory.createAnnotationEntry("@$useSiteTargetRendered${elementContext.classId.asFqNameString()}")
            }
        }

        val added = optInAnnotation.parent.addAfter(toAdd, optInAnnotation) as KtAnnotationEntry
        shortenReferences(added)

        if (optInAnnotation.valueArgumentList?.arguments?.size == 1)
            optInAnnotation.delete()
        else
            optInAnnotation.valueArgumentList?.removeArgument(elementContext.element)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("intention.family.name.convert.opt.in.marker.to.requirement")

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtValueArgument): Context? {
        val lit = element.getArgumentExpression() as? KtClassLiteralExpression ?: return null

        val parentAnnotation = element.parentOfType<KtAnnotationEntry>() ?: return null
        if (!parentAnnotation.valueArguments.contains(element)) // forbid nested expressions
            return null

        if (
            parentAnnotation.parent is KtParameter ||
            parentAnnotation.parent is KtExpression ||
            (parentAnnotation.parent.parent as? KtProperty)?.isLocal == true ||
            parentAnnotation.parent.parent is KtParameter ||
            parentAnnotation.parent is KtFileAnnotationList
        ) // inapplicable on those elements
            return null

        val constructorSymbol = parentAnnotation.resolveSymbol() ?: return null
        val classSymbol = constructorSymbol.containingSymbol as? KaNamedClassSymbol ?: return null

        if (classSymbol.classId != OptInNames.OPT_IN_CLASS_ID)
            return null

        val klsSymbol = lit.resolveSymbol() ?: return null
        val klsId = (klsSymbol as? KaNamedClassSymbol)?.classId ?: return null

        return Context(klsId, element)
    }

    internal data class Context(val classId: ClassId, val element: KtValueArgument)
}
