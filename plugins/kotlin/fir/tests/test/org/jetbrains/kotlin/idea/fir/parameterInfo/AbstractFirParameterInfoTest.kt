// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.parameterInfo

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.parameterInfo.AbstractParameterInfoTest
import org.jetbrains.kotlin.idea.test.runAll
import java.nio.file.Paths

abstract class AbstractFirParameterInfoTest : AbstractParameterInfoTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun doTest(fileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Paths.get(fileName), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(fileName)
        }
    }
}