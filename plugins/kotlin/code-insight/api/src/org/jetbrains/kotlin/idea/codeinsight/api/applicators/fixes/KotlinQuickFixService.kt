// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi

class KotlinQuickFixService {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinQuickFixService = service()
    }

    private val list = KotlinQuickFixesList.createCombined(KotlinQuickFixRegistrar.allQuickFixesList())

    context(KtAnalysisSession)
    fun getQuickFixesFor(diagnostic: KtDiagnosticWithPsi<*>): List<IntentionAction> {
        return list.getQuickFixesFor(diagnostic)
    }
}

abstract class KotlinQuickFixRegistrar {
    protected abstract val list: KotlinQuickFixesList

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinQuickFixRegistrar> =
            ExtensionPointName.create("org.jetbrains.kotlin.codeinsight.quickfix.registrar")

        fun allQuickFixesList() = EP_NAME.extensionList.map { it.list }
    }
}