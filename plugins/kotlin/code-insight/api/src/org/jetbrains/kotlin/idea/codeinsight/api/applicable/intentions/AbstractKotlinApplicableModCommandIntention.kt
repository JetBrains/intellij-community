// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableTool
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableWithAnalyze
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

abstract class AbstractKotlinApplicableModCommandIntention<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : AbstractKotlinApplicableModCommandIntentionBase<ELEMENT>(elementType), KotlinApplicableTool<ELEMENT> {
    final override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean {
        return super.isApplicableTo(element, caretOffset) && isApplicableWithAnalyze(element)
    }

    override fun apply(element: ELEMENT, project: Project, editor: Editor?) {
        throw UnsupportedOperationException("apply(ELEMENT, Project, Editor?) should not be invoked")
    }

}