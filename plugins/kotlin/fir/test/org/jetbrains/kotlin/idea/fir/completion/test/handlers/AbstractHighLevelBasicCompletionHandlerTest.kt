// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.completion.test.firFileName
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches

abstract class AbstractHighLevelBasicCompletionHandlerTest : AbstractBasicCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun fileName(): String = firFileName(super.fileName(), testDataDirectory, ".after")
}