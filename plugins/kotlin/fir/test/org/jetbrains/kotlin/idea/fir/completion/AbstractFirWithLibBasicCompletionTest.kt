// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmWithLibBasicCompletionTest
import org.jetbrains.kotlin.idea.completion.test.firFileName
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirWithLibBasicCompletionTest: AbstractJvmWithLibBasicCompletionTest() {
    override val captureExceptions: Boolean = false

    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          ThrowableRunnable { project.invalidateCaches() },
          ThrowableRunnable { super.tearDown() }
        )
    }

    override fun fileName(): String = firFileName(super.fileName(), testDataDirectory)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalFirTestFile(dataFile())
        }
    }
}