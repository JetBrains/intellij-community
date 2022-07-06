// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi

class KtQuickFixService {
    private val list = KtQuickFixesList.Companion.createCombined(KtQuickFixRegistrar.allQuickFixesList())

    fun KtAnalysisSession.getQuickFixesFor(diagnostic: KtDiagnosticWithPsi<*>): List<IntentionAction> =
        with(list) { getQuickFixesFor(diagnostic) }
}

abstract class KtQuickFixRegistrar {
    protected abstract val list: KtQuickFixesList

    companion object {
        private val EP_NAME: ExtensionPointName<KtQuickFixRegistrar> =
            ExtensionPointName.create("org.jetbrains.kotlin.ktQuickFixRegistrar")

        fun allQuickFixesList() = EP_NAME.extensionList.map { it.list }
    }
}