// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion.wheigher

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.completion.test.weighers.AbstractBasicCompletionWeigherTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.disableKotlinOfficialCodeStyle
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractHighLevelWeigherTest : AbstractBasicCompletionWeigherTest() {
    override fun isFirPlugin(): Boolean = true

    override val captureExceptions: Boolean = false

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            test()
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() },
        )
    }
}