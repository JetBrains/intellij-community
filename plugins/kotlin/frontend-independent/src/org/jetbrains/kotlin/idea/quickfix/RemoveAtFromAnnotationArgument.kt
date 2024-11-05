// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.firstLeaf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class RemoveAtFromAnnotationArgument(
    element: KtAnnotationEntry,
) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("remove.from.annotation.argument")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        val firstLeaf = element.firstLeaf()
        assert(firstLeaf.text == "@") {
            "Expected '@' at the beginning of the annotation argument, but found '${firstLeaf.text}'"
        }
        firstLeaf.delete()
    }
}
