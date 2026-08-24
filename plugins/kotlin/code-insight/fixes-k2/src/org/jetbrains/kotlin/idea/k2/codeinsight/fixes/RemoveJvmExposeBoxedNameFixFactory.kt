// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.deleteValueArgument
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Both diagnostics are reported on the `jvmName` argument expression of `@JvmExposeBoxed`,
 * so the argument itself is what has to go — the annotation stays.
 */
internal object RemoveJvmExposeBoxedNameFixFactory {

    private fun removeNameArgument(psi: PsiElement): List<RemoveJvmExposeBoxedNameFix> {
        val argument = psi.parent as? KtValueArgument ?: return emptyList()
        return listOf(RemoveJvmExposeBoxedNameFix(argument))
    }

    val inapplicableWithName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InapplicableJvmExposeBoxedWithName ->
            removeNameArgument(diagnostic.psi)
        }

    /**
     * Secondary fix: dropping the duplicated name leaves the existing `@JvmName` authoritative.
     * The primary fix is [ChangeJvmExposeBoxedNameFixFactory].
     */
    val cannotBeTheSameAsJvmName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotBeTheSameAsJvmName ->
            removeNameArgument(diagnostic.psi)
        }
}

private class RemoveJvmExposeBoxedNameFix(
    element: KtValueArgument,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtValueArgument>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.remove.argument.text")

    override fun invoke(
        context: ActionContext,
        element: KtValueArgument,
        updater: ModPsiUpdater,
    ) {
        val argumentList = element.parent as? KtValueArgumentList ?: return
        argumentList.deleteValueArgument(element)
        if (argumentList.arguments.isEmpty()) {
            argumentList.delete()
        }
    }
}
