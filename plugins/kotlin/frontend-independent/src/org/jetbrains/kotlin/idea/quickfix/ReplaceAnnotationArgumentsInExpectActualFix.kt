// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal class ReplaceAnnotationArgumentsInExpectActualFix(
    @Nls private val text: String,
    private val copyFromAnnotationEntry: KtAnnotationEntry,
    copyToAnnotationEntry: KtAnnotationEntry,
) : PsiUpdateModCommandAction<KtAnnotationEntry>(copyToAnnotationEntry) {
    override fun getFamilyName(): @IntentionFamilyName String = text

    override fun invoke(context: ActionContext, element: KtAnnotationEntry, updater: ModPsiUpdater) {
        val newValueArguments = copyFromAnnotationEntry.valueArgumentList?.copy()
        element.valueArgumentList?.delete()
        if (newValueArguments != null) {
            element.addAfter(newValueArguments, element.lastChild)
        }
    }
}