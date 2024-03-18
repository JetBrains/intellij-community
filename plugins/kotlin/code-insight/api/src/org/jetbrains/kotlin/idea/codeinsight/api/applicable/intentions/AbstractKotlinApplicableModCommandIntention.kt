// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>
) : AbstractKotlinApplicableModCommandIntentionBase<ELEMENT>(elementType), KotlinApplicableTool<ELEMENT> {

    override fun isElementApplicable(element: ELEMENT, context: ActionContext): Boolean {
        if (!super.isElementApplicable(element, context)) return false

        val applicableByAnalyze = analyze(element) { isApplicableByAnalyze(element) }
        return applicableByAnalyze
    }

    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean =
        super.isApplicableTo(element, caretOffset) && isApplicableWithAnalyze(element)

    final override fun apply(element: ELEMENT, project: Project, editor: Editor?) {
        throw UnsupportedOperationException("apply(ELEMENT, Project, Editor?) should not be invoked")
    }

    abstract fun apply(element: ELEMENT, context: ActionContext, updater: ModPsiUpdater)

    final override fun invoke(context: ActionContext, element: ELEMENT, updater: ModPsiUpdater) {
        apply(element, context, updater)
    }

}