// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ReplaceJvmFieldWithConstFix(annotation: KtAnnotationEntry) : PsiUpdateModCommandAction<KtAnnotationEntry>(annotation) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.jvmfield.with.const")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater
    ) {
        val property = element.getParentOfType<KtProperty>(false) ?: return
        element.delete()
        property.addModifier(KtTokens.CONST_KEYWORD)
    }
}