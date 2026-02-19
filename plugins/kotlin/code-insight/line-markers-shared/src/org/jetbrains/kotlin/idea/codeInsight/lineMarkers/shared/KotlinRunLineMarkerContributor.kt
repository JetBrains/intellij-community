// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.findMainOwner
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.KotlinRunLineMarkerHider
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun isDumbAware(): Boolean = true

    override fun getInfo(element: PsiElement): Info? = null

    override fun getSlowInfo(element: PsiElement): Info? {
        val function = element.parent as? KtNamedFunction ?: return null
        if (function.nameIdentifier != element) return null

        if (KotlinRunLineMarkerHider.shouldHideRunLineMarker(element)) return null

        val detector = KotlinMainFunctionDetector.getInstanceDumbAware(element.project)
        if (!detector.isMain(function)) return null

        if (DumbService.isDumb(element.project) && !detector.hasSingleMain(function)) return null

        val module = function.containingKtFile.module ?: return null
        if (module.isTestModule) return null
        if (!module.platform.idePlatformKind.tooling.acceptsAsEntryPoint(function)) return null


        val icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.TestStateRun)
        return Info(icon, ExecutorAction.getActions(Int.MAX_VALUE), null)
    }

    private fun KotlinMainFunctionDetector.hasSingleMain(mainFunction: KtNamedFunction): Boolean {
        return this.findMainOwner(mainFunction) != null
    }
}
