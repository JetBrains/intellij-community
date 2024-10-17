// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

object RestrictedRetentionForExpressionAnnotationFactory : KotlinIntentionActionsFactory() {

    private val sourceRetention = "${StandardNames.FqNames.annotationRetention.asString()}.${AnnotationRetention.SOURCE.name}"
    private val sourceRetentionAnnotation = "@${StandardNames.FqNames.retention.asString()}($sourceRetention)"

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val annotationEntry = diagnostic.psiElement as? KtAnnotationEntry ?: return emptyList()
        val containingClass = annotationEntry.containingClass() ?: return emptyList()
        val retentionAnnotation = containingClass.annotation(StandardNames.FqNames.retention)
        val targetAnnotation = containingClass.annotation(StandardNames.FqNames.target)
        val expressionTargetArgument = if (targetAnnotation != null) findExpressionTargetArgument(targetAnnotation) else null

        return buildList {
            if (expressionTargetArgument != null) {
                add(RemoveExpressionTargetFix(expressionTargetArgument).asIntention())
            }

            val retentionFix = retentionAnnotation?.let(::ChangeRetentionToSourceFix) ?: AddSourceRetentionFix(containingClass)
            add(retentionFix.asIntention())
        }
    }

    private fun KtClass.annotation(fqName: FqName): KtAnnotationEntry? {
        return annotationEntries.firstOrNull {
            it.typeReference?.text?.endsWith(fqName.shortName().asString()) == true
                    && analyze()[BindingContext.TYPE, it.typeReference]?.constructor?.declarationDescriptor?.fqNameSafe == fqName
        }
    }

    private fun findExpressionTargetArgument(targetAnnotation: KtAnnotationEntry): KtValueArgument? {
        val valueArgumentList = targetAnnotation.valueArgumentList ?: return null
        if (targetAnnotation.lambdaArguments.isNotEmpty()) return null

        for (valueArgument in valueArgumentList.arguments) {
            val argumentExpression = valueArgument.getArgumentExpression() ?: continue
            if (argumentExpression.text.contains(KotlinTarget.EXPRESSION.toString())) {
                return valueArgument
            }
        }

        return null
    }

    private class AddSourceRetentionFix(
        element: KtClass,
    ) : PsiUpdateModCommandAction<KtClass>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("add.source.retention")

        override fun invoke(
            context: ActionContext,
            element: KtClass,
            updater: ModPsiUpdater,
        ) {
            val added = element.addAnnotationEntry(KtPsiFactory(context.project).createAnnotationEntry(sourceRetentionAnnotation))
            ShortenReferencesFacility.getInstance().shorten(added)
        }
    }

    private class ChangeRetentionToSourceFix(
        element: KtAnnotationEntry
    ) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("change.existent.retention.to.source")

        override fun invoke(
            context: ActionContext,
            element: KtAnnotationEntry,
            updater: ModPsiUpdater,
        ) {
            val psiFactory = KtPsiFactory(context.project)
            val added = if (element.valueArgumentList == null) {
                element.add(psiFactory.createCallArguments("($sourceRetention)")) as KtValueArgumentList
            } else {
                if (element.valueArguments.isNotEmpty()) {
                    element.valueArgumentList?.removeArgument(0)
                }
                element.valueArgumentList?.addArgument(psiFactory.createArgument(sourceRetention))
            }
            if (added != null) {
                ShortenReferencesFacility.getInstance().shorten(added)
            }
        }
    }

    private class RemoveExpressionTargetFix(
        element: KtValueArgument,
    ) : PsiUpdateModCommandAction<KtValueArgument>(element) {

        override fun getFamilyName(): String = KotlinBundle.message("remove.expression.target")

        override fun invoke(
            context: ActionContext,
            element: KtValueArgument,
            updater: ModPsiUpdater,
        ) {
            val argumentList = element.parent as? KtValueArgumentList ?: return

            if (argumentList.arguments.size == 1) {
                val annotation = argumentList.parent as? KtAnnotationEntry ?: return
                annotation.delete()
            } else {
                argumentList.removeArgument(element)
            }
        }
    }
}
