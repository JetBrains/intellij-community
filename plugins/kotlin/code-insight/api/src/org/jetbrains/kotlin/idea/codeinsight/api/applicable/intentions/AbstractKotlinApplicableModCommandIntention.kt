// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
    predicate: (ELEMENT, ActionContext) -> Boolean,
    applicablePredicate: AbstractKotlinApplicablePredicate<ELEMENT>? = null
) : AbstractKotlinApplicableModCommandIntentionBase<ELEMENT>(elementType, predicate, applicablePredicate), KotlinApplicableTool<ELEMENT> {

    constructor(clazz: KClass<ELEMENT>) : this(clazz, ALWAYS_TRUE)

    constructor(clazz: KClass<ELEMENT>, kotlinApplicablePredicate: AbstractKotlinApplicablePredicate<ELEMENT>) :
            this(clazz, kotlinApplicablePredicate::apply, kotlinApplicablePredicate)

    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean =
        super.isApplicableTo(element, caretOffset) && isApplicableWithAnalyze(element)

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: ELEMENT): Boolean = 
        applicablePredicate?.isApplicableByAnalyze(element) ?: true

    override fun apply(element: ELEMENT, project: Project, editor: Editor?) {
        throw UnsupportedOperationException("apply(ELEMENT, Project, Editor?) should not be invoked")
    }

}