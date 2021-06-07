// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.nio.file.Paths
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractHighLevelMultiFileJvmBasicCompletionTest : KotlinFixtureCompletionBaseTestCase() {

    override val testDataDirectory: File
        get() = super.testDataDirectory.resolve(getTestName(false))

    override val captureExceptions: Boolean = false

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFile().toPath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            test()
        }
    }

    override fun fileName(): String = getTestName(false) + ".kt"

    override fun configureFixture(testPath: String) {
        // We need to copy all files from the testDataPath (= "") to the tested project
        myFixture.copyDirectoryToProject("", "")
        super.configureFixture(testPath)
    }

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
    override fun getPlatform(): TargetPlatform = JvmPlatforms.unspecifiedJvmPlatform
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}