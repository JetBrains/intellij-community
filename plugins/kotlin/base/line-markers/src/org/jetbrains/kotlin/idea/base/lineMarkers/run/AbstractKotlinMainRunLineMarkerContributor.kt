// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.lineMarkers.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.psi.KtNamedFunction

@ApiStatus.Internal
abstract class AbstractKotlinMainRunLineMarkerContributor : RunLineMarkerContributor() {
    /**
     * Additional condition on main function in case that it should not be possible to run.
     *
     * Note that [function] is already checked to have correct signature and name.
     */
    protected abstract fun acceptEntryPoint(function: KtNamedFunction): Boolean

    override fun getInfo(element: PsiElement): Info? = null

    override fun getSlowInfo(element: PsiElement): Info? {
        val function = element.parent as? KtNamedFunction ?: return null

        if (function.nameIdentifier != element) return null

        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return null

        if (!acceptEntryPoint(function)) return null

        return Info(IconManager.getInstance().getPlatformIcon(PlatformIcons.TestStateRun), null, *ExecutorAction.getActions(Int.MAX_VALUE))
    }
}