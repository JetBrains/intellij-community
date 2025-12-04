// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.findUsages

import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.idea.test.mapFromK1Provider
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1FindUsagesTest : AbstractFindUsagesTest() {
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> {
        return k1DiagnosticProviderForFindUsages()
    }
}

fun k1DiagnosticProviderForFindUsages() = mapFromK1Provider { it: KtFile -> it.analyzeWithAllCompilerChecks().bindingContext.diagnostics }