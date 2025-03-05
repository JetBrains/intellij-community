// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction

@Suppress("LightServiceMigrationCode")
class KotlinQuickFixService {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinQuickFixService = service()
    }

    private val list = KotlinQuickFixesList.createCombined(KotlinQuickFixRegistrar.allQuickFixesList())
    private val importOnTheFlyList = KotlinQuickFixesList.createCombined(KotlinQuickFixRegistrar.allImportOnTheFlyQuickFixList())

    fun KaSession.getQuickFixesFor(diagnostic: KaDiagnosticWithPsi<*>): List<IntentionAction> =
        with(list) { getQuickFixesFor(diagnostic) }

    fun KaSession.getImportQuickFixesFor(diagnostic: KaDiagnosticWithPsi<*>): List<KotlinImportQuickFixAction<*>> =
        with(importOnTheFlyList) { getQuickFixesFor(diagnostic).filterIsInstance<KotlinImportQuickFixAction<*>>() }
}

abstract class KotlinQuickFixRegistrar {
    protected abstract val list: KotlinQuickFixesList

    /**
     * Quick fixes that are allowed to be used for importing references on the fly.
     */
    protected open val importOnTheFlyList: KotlinQuickFixesList = KotlinQuickFixesList.createCombined()

    companion object {
        private val EP_NAME: ExtensionPointName<KotlinQuickFixRegistrar> =
            ExtensionPointName.create("org.jetbrains.kotlin.codeinsight.quickfix.registrar")

        fun allQuickFixesList(): List<KotlinQuickFixesList> =
            EP_NAME.extensionList.map { it.list }
        fun allImportOnTheFlyQuickFixList(): List<KotlinQuickFixesList> =
            EP_NAME.extensionList.map { it.importOnTheFlyList }
    }
}