// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractBasicCompletionHandlerTest
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractSmartCompletionHandlerTest
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractHighLevelSmartCompletionHandlerTest : AbstractSmartCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun type(s: String) {
        // This is necessary because typing might cause completion to be invoked, which will be
        // run on EDT and using a write action from unit tests (and only unit tests).
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
               fixture.type(s)
            }
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, IgnoreTests.FileExtension.FIR, ".after")
}