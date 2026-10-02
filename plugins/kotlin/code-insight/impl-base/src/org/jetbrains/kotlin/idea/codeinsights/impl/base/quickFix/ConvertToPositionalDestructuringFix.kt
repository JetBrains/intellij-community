// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.convertDestructuringToPositionalForm
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration

@ApiStatus.Internal
class ConvertToPositionalDestructuringFix(declaration: KtDestructuringDeclaration, private val supportsFixAll: Boolean = true) :
    KotlinPsiUpdateModCommandAction.ElementContextless<KtDestructuringDeclaration>(declaration) {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.positional.destructuring.migration.fix")

    override fun addFixAllOption(context: ActionContext, element: KtDestructuringDeclaration): Boolean = supportsFixAll

    override fun invoke(
        context: ActionContext,
        element: KtDestructuringDeclaration,
        updater: ModPsiUpdater
    ) {
        convertDestructuringToPositionalForm(element)
    }
}
