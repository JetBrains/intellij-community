// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.platform.tooling
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.run.KotlinMainFunctionLocatingService
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val function = element.parent as? KtNamedFunction ?: return null

        if (function.nameIdentifier != element) return null

        val mainLocatingService = KotlinMainFunctionLocatingService.getInstance()
        if (!mainLocatingService.isMain(function)) return null

        val platform = function.containingKtFile.module?.platform ?: return null
        if (platform.isCommon() || !platform.idePlatformKind.tooling.acceptsAsEntryPoint(function)) return null

        return Info(AllIcons.RunConfigurations.TestState.Run, null, *ExecutorAction.getActions(Int.MAX_VALUE))
    }
}
