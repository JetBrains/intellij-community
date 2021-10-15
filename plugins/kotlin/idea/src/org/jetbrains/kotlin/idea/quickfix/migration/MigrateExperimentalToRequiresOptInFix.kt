// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.migration

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.DEPRECATION
import org.jetbrains.kotlin.diagnostics.Errors.DEPRECATION_ERROR
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.idea.quickfix.CleanupFix
import org.jetbrains.kotlin.idea.quickfix.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * The quick fix to replace a deprecated `@Experimental` annotation with the new `@RequiresOptIn` annotation.
 */
class MigrateExperimentalToRequiresOptInFix(
    annotationEntry: KtAnnotationEntry,
    private val requiresOptInInnerText: String?
) : KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry), CleanupFix
{
    override fun getText(): String = KotlinBundle.message("fix.opt_in.migrate.experimental.annotation.replace")

    override fun getFamilyName(): String = KotlinBundle.message("fix.opt_in.migrate.experimental.annotation.replace")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val oldAnnotationEntry = element ?: return
        val owner = oldAnnotationEntry.getStrictParentOfType<KtModifierListOwner>() ?: return
        val added = owner.addAnnotation(
            OptInNames.REQUIRES_OPT_IN_FQ_NAME,
            requiresOptInInnerText,
            useSiteTarget = null,
            searchForExistingEntry = false // We don't want to resolve existing annotations in the write action
        )
        if (added) oldAnnotationEntry.delete()
    }

    /**
     * Quick fix factory to create remove/replace quick fixes for deprecated `@Experimental` annotations.
     *
     * If the annotated expression has both `@Experimental` annotation and `@RequiresOptIn` annotation,
     * the "Remove annotation" action is proposed to get rid of the obsolete `@Experimental` annotation
     * (we don't check if the `level` arguments match in both annotations).
     *
     * If there is only an `@Experimental` annotation, the factory generates a quick fix a "Replace annotation"
     * quick fix that removes the `@Experimental` annotation and creates a `@RequiresOptIn` annotation
     * with the matching value of the `level` argument.
     */
    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            if (diagnostic.factory !in setOf(DEPRECATION, DEPRECATION_ERROR)) return null
            val constructorCallee = diagnostic.psiElement.getStrictParentOfType<KtConstructorCalleeExpression>() ?: return null
            val annotationEntry = constructorCallee.parent?.safeAs<KtAnnotationEntry>() ?: return null
            val annotationDescriptor = annotationEntry.resolveToDescriptorIfAny() ?: return null
            if (annotationDescriptor.fqName == OptInNames.OLD_EXPERIMENTAL_FQ_NAME) {
                val annotationOwner = annotationEntry.getStrictParentOfType<KtModifierListOwner>() ?: return null
                if (annotationOwner.findAnnotation(OptInNames.REQUIRES_OPT_IN_FQ_NAME) != null) {
                    return RemoveAnnotationFix(KotlinBundle.message("fix.opt_in.migrate.experimental.annotation.remove"), annotationEntry)
                } else {
                    val level = annotationDescriptor.argumentValue("level")?.safeAs<EnumValue>()?.enumEntryName?.asString()
                    val requiresOptInInnerText = when (level) {
                        "ERROR" -> "level = RequiresOptIn.Level.ERROR"
                        "WARNING" -> "level = RequiresOptIn.Level.WARNING"
                        else -> null
                    }
                    return MigrateExperimentalToRequiresOptInFix(annotationEntry, requiresOptInInnerText)
                }
            } else {
                return null
            }
        }
    }
}
