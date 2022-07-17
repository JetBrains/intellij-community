// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.psi.PsiElement
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.findUsages.AbstractKotlinFindUsagesWithLibraryTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths

abstract class AbstractKotlinFindUsagesWithLibraryFirTest : AbstractKotlinFindUsagesWithLibraryTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun <T : PsiElement> doTest(path: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(
            Paths.get(path),
            IgnoreTests.DIRECTIVES.FIR_COMPARISON,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doTest<T>(path)
        }
    }
}