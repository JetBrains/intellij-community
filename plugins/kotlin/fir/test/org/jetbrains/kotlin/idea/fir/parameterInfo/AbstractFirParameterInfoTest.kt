// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.parameterInfo

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.parameterInfo.AbstractParameterInfoTest
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths

abstract class AbstractFirParameterInfoTest : AbstractParameterInfoTest() {
    override fun isFirPlugin() = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun doTest(fileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(fileName), IgnoreTests.DIRECTIVES.IGNORE_FIR) {
            super.doTest(fileName)
        }
    }
}