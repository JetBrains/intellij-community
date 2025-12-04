// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1KotlinGroupUsagesBySimilarityTest : AbstractKotlinGroupUsagesBySimilarityTest() {
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> {
        return k1DiagnosticProviderForFindUsages()
    }
}