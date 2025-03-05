// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

object RestrictedRetentionForExpressionAnnotationFactory : KotlinIntentionActionsFactory() {

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
}
