// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.base.fe10.analysis.classId
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.*
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
        val annotatedElement = annotationEntry.getParentOfTypes(
            strict = true,
            KtProperty::class.java,
            KtParameter::class.java,
        ) ?: return emptyList()

        val bindingContext = annotationEntry.analyze(BodyResolveMode.PARTIAL)
        val annotationClassId = bindingContext[BindingContext.ANNOTATION, annotationEntry]?.classId ?: return emptyList()

        return OptInAnnotationWrongTargetFixUtils.collectQuickFixes(annotatedElement, annotationEntry, annotationClassId)
    }
}
