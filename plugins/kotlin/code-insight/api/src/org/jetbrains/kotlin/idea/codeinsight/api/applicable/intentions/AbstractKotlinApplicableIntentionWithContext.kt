// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.prepareContextWithAnalyzeAllowEdt
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI with [apply] given some [CONTEXT] from [prepareContext] if the intention is applicable via [isApplicableByPsi] and
 * [prepareContext].
 */
abstract class AbstractKotlinApplicableIntentionWithContext<ELEMENT : KtElement, CONTEXT>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableIntentionBase<ELEMENT>(elementType), KotlinApplicableToolWithContext<ELEMENT, CONTEXT> {
    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        if (!super.isApplicableTo(element, caretOffset)) return false
        val context = prepareContextWithAnalyzeAllowEdt(element, needsReadAction = false) ?: return false

        val actionText = getActionName(element, context)
        setTextGetter { actionText }
        return true
    }

    final override fun applyTo(element: ELEMENT, project: Project, editor: Editor?) {
        val context = prepareContextWithAnalyzeAllowEdt(element, needsReadAction = true) ?: return
        runWriteActionIfPhysical(element) {
            apply(element, context, project, editor)
        }
    }

    final override fun startInWriteAction(): Boolean =
        // `applyTo` should start without a write action because it first uses `analyzeWithReadAction` to get the context. Also,
        // `getContext` when called from `applyTo` should not have access to a write action for `element` to discourage mutating `element`.
        false
}