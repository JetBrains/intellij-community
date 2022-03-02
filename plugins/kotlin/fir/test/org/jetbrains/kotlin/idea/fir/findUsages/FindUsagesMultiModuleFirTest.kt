// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.findUsages.FindUsagesMultiModuleTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths

class FindUsagesMultiModuleFirTest : FindUsagesMultiModuleTest() {
    override val isFirPlugin: Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun doFindUsagesTest() {
        IgnoreTests.runTestIfEnabledByFileDirective(
            Paths.get(mainFile.virtualFilePath),
            IgnoreTests.DIRECTIVES.FIR_COMPARISON,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doFindUsagesTest()
        }
    }
}