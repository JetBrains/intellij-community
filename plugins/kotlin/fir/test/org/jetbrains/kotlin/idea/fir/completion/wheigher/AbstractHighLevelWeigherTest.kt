// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.wheigher

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.completion.test.firFileName
import org.jetbrains.kotlin.idea.completion.test.weighers.AbstractBasicCompletionWeigherTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractHighLevelWeigherTest : AbstractBasicCompletionWeigherTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val captureExceptions: Boolean = false

    override fun fileName(): String = firFileName(super.fileName(), testDataDirectory)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2, ".after") {
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