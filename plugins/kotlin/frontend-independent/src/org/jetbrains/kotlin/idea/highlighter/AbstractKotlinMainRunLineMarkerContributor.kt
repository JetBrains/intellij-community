// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.run.KotlinMainFunctionLocatingService
import org.jetbrains.kotlin.psi.KtNamedFunction

abstract class AbstractKotlinMainRunLineMarkerContributor : RunLineMarkerContributor() {
    /**
     * Additional condition on main function in case that it should not be possible to run.
     *
     * Note that [function] is already checked to have correct signature and name.
     */
    protected abstract fun acceptEntryPoint(function: KtNamedFunction): Boolean

    override fun getInfo(element: PsiElement): Info? {
        val function = element.parent as? KtNamedFunction ?: return null

        if (function.nameIdentifier != element) return null

        val mainLocatingService = KotlinMainFunctionLocatingService.getInstance()
        if (!mainLocatingService.isMain(function)) return null

        if (!acceptEntryPoint(function)) return null

        return Info(AllIcons.RunConfigurations.TestState.Run, null, *ExecutorAction.getActions(Int.MAX_VALUE))
    }
}