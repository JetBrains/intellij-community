// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import org.jetbrains.kotlin.idea.fir.findUsages.k2DiagnosticProviderForFindUsages
import org.jetbrains.kotlin.idea.search.refIndex.AbstractFindUsagesWithCompilerReferenceIndexTest
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFindUsagesWithCompilerReferenceIndexFirTest : AbstractFindUsagesWithCompilerReferenceIndexTest() {
    override val ignoreLog: Boolean get() = true
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> = k2DiagnosticProviderForFindUsages()
}
