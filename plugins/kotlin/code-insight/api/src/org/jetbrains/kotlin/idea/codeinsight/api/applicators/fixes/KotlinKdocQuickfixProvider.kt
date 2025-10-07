// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName

@ApiStatus.Internal
interface KotlinKdocQuickfixProvider {
    fun getKdocQuickFixesFor(kdocName: KDocName): List<IntentionAction>

    companion object {
        private val EP_NAME =
            ExtensionPointName.create<KotlinKdocQuickfixProvider>("org.jetbrains.kotlin.codeinsight.quickfix.kdocQuickfixProvider")

        fun getKdocQuickFixesFor(kdocName: KDocName): List<IntentionAction> =
            EP_NAME.extensionList.flatMap { it.getKdocQuickFixesFor(kdocName) }
    }
}
