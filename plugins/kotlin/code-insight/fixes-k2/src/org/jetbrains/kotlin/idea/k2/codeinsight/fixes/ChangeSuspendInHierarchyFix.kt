// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.searching.inheritors.findHierarchyWithSiblings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class ChangeSuspendInHierarchyFix(
    function: KtNamedFunction,
    private val addModifier: Boolean,
) : PsiBasedModCommandAction<KtNamedFunction>(function) {

    override fun getFamilyName(): String = if (addModifier) {
        KotlinBundle.message("fix.change.suspend.hierarchy.add")
    } else {
        KotlinBundle.message("fix.change.suspend.hierarchy.remove")
    }

    override fun perform(context: ActionContext, element: KtNamedFunction): ModCommand {
        val functionsToProcess = setOf(element) + element.findHierarchyWithSiblings().filterIsInstance<KtNamedFunction>()

        return ModCommand.psiUpdate(context) { modPsiUpdater: ModPsiUpdater ->
            val writableFunctionsToProcess = functionsToProcess.map { modPsiUpdater.getWritable(it) }

            writableFunctionsToProcess.forEach { function ->
                if (addModifier) {
                    function.addModifier(KtTokens.SUSPEND_KEYWORD)
                } else {
                    function.removeModifier(KtTokens.SUSPEND_KEYWORD)
                }
            }
        }
    }
}
