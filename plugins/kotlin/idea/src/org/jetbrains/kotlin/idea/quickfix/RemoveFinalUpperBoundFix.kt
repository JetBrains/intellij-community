// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveFinalUpperBoundFix(val typeReference: KtTypeReference) : KotlinQuickFixAction<KtTypeReference>(typeReference) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        typeReference.getStrictParentOfType<KtTypeParameter>()?.extendsBound = null
    }

    override fun getText() = KotlinBundle.message("remove.final.upper.bound")

    override fun getFamilyName() = text

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) = RemoveFinalUpperBoundFix(Errors.FINAL_UPPER_BOUND.cast(diagnostic).psiElement)
    }
}