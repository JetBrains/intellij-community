// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion.wheigher

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.weighers.AbstractCompletionWeigherTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractHighLevelWeigherTest(completionType: CompletionType, relativeTestDataPath: String):
    AbstractCompletionWeigherTest(completionType, relativeTestDataPath) {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val captureExceptions: Boolean = false

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, IgnoreTests.FileExtension.FIR)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}
abstract class AbstractBasicCompletionWeigherTest : AbstractHighLevelWeigherTest(CompletionType.BASIC, "weighers/basic")
abstract class AbstractSmartCompletionWeigherTest : AbstractHighLevelWeigherTest(CompletionType.SMART, "weighers/smart")
