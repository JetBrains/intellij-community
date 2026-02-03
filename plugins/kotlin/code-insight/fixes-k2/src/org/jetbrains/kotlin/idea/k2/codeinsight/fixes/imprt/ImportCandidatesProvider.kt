// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.name.Name

internal interface ImportCandidatesProvider {
    context(_: KaSession)
    fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate>
}