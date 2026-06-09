// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal class ConvertRequirementToOptInIntention :
    KotlinApplicableModCommandAction<KtAnnotationEntry, ConvertRequirementToOptInIntention.Context>(KtAnnotationEntry::class) {
    internal data class Context(
        val fqName: FqName,
        val existingOptInAnnotation: KtAnnotationEntry?,
        val annotationTarget: KtAnnotationUseSiteTarget?
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtAnnotationEntry,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)
        val optin = elementContext.existingOptInAnnotation
            ?: createOptinAnnotation(element, elementContext.annotationTarget?.getAnnotationUseSiteTarget())

        element.delete()
        val argsList = optin.getOrCreateArguments()

        val added = argsList.addArgument(psiFactory.createArgument(elementContext.fqName.asClassLiteral()))

        shortenReferences(added)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("intention.family.name.convert.requirement.to.optin")

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtAnnotationEntry): Context? {
        if (element.parent.parent is KtParameter) return null // inapplicable on those elements

        val constructorSymbol = element.resolveSymbol() ?: return null
        val classSymbol = constructorSymbol.containingSymbol as? KaNamedClassSymbol ?: return null

        if (!classSymbol.annotations.any {
                it.classId == OptInNames.REQUIRES_OPT_IN_CLASS_ID
            }) return null

        val classId = classSymbol.classId ?: return null

        val useSiteTarget = element.useSiteTarget

        val existingOptInAnnotation =
            (element.findParentOfType<KtModifierList>()?.annotationEntries
                ?: (element.parent as? KtExpression)?.getAnnotationEntries())
                ?.find { it.resolveSymbol()?.containingClassId == OptInNames.OPT_IN_CLASS_ID && it.useSiteTarget?.getAnnotationUseSiteTarget() == useSiteTarget?.getAnnotationUseSiteTarget() }

        return Context(classId.asSingleFqName(), existingOptInAnnotation, useSiteTarget)
    }
}

private fun FqName.asClassLiteral(): String = "${this.asString()}::class"

private fun createOptinAnnotation(anchor: PsiElement, annotationTarget: AnnotationUseSiteTarget?): KtAnnotationEntry {
    val psiFactory = KtPsiFactory(anchor.project)
    return when (annotationTarget) {
        AnnotationUseSiteTarget.FILE -> {
            anchor.parent.addAfter(psiFactory.createFileAnnotation("OptIn"), anchor)
        }

        else -> {
            val useSiteTargetRendered = annotationTarget?.renderName?.let { "$it:" } ?: ""
            anchor.parent.addAfter(psiFactory.createAnnotationEntry("@${useSiteTargetRendered}OptIn"), anchor)
        }
    } as KtAnnotationEntry
}

private fun KtAnnotationEntry.getOrCreateArguments(): KtValueArgumentList {
    valueArgumentList?.let { return it }

    val arguments = KtPsiFactory(this.project).createCallArguments("()")

    return addAfter(arguments, lastChild) as KtValueArgumentList
}
