// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import org.jetbrains.kotlin.findUsages.AbstractKotlinScriptFindUsagesTest
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractKotlinScriptFindUsagesFirTest : AbstractKotlinScriptFindUsagesTest() {

    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> = k2DiagnosticProviderForFindUsages()

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

}