// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider

internal interface ImportCandidatesProvider {
    context(KaSession)
    fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate>
}