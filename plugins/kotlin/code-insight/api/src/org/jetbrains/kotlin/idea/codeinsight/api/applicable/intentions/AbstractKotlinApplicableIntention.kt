// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyzeAllowEdt
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * Applies a fix to the PSI with [apply] if the intention is applicable via [isApplicableByPsi] and [isApplicableByAnalyze].
 *
 * If [apply] needs to use the Analysis API, inherit from [AbstractKotlinModCommandWithContext] instead.
 */
@Deprecated("Use AbstractKotlinApplicableModCommandIntention")
abstract class AbstractKotlinApplicableIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableIntentionBase<ELEMENT>(elementType), KotlinApplicableTool<ELEMENT> {
    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        if (!super.isApplicableTo(element, caretOffset)) return false
        if (!isApplicableWithAnalyzeAllowEdt(element)) return false

        val actionText = getActionName(element)
        setTextGetter { actionText }
        return true
    }

    final override fun applyTo(element: ELEMENT, project: Project, editor: Editor?) = apply(element, project, editor)

    final override fun startInWriteAction(): Boolean = shouldApplyInWriteAction()
}