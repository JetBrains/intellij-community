// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? = null

    override fun getSlowInfo(element: PsiElement): Info? {
        val function = element.parent as? KtNamedFunction ?: return null
        if (function.nameIdentifier != element) return null
        if (!KotlinMainFunctionDetector.getInstance().isMain(function)) return null

        val platform = function.containingKtFile.module?.platform ?: return null
        if (platform.isCommon()) return null
        if (!platform.idePlatformKind.tooling.acceptsAsEntryPoint(function)) return null

        val icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.TestStateRun)
        return Info(icon, null, *ExecutorAction.getActions(Int.MAX_VALUE))
    }
}