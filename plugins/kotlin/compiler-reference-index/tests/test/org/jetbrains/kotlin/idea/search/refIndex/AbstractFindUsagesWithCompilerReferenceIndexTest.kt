// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.refIndex

import org.jetbrains.kotlin.findUsages.k1DiagnosticProviderForFindUsages
import org.jetbrains.kotlin.idea.test.Diagnostic
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK1FindUsagesWithCompilerReferenceIndexTest : AbstractFindUsagesWithCompilerReferenceIndexTest() {
    override fun getDiagnosticProvider(): (KtFile) -> List<Diagnostic> {
        return k1DiagnosticProviderForFindUsages()
    }
}

