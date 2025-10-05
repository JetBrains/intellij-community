// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.completion.test.KotlinFixtureCompletionBaseTestCase
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.io.File

abstract class AbstractHighLevelMultiFileJvmBasicCompletionTest : KotlinFixtureCompletionBaseTestCase() {

    override val testDataDirectory: File
        get() = super.testDataDirectory.resolve(getTestName(false))

    override val captureExceptions: Boolean = false

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            test()
        }
    }

    override fun extraLookupElementCheck(lookupElement: LookupElement) {
        SerializabilityChecker.checkLookupElement(lookupElement, myFixture.project)
    }

    override fun fileName(): String = getTestName(false) + ".kt"

    override fun configureFixture(testPath: String) {
        // We need to copy all files from the testDataPath (= "") to the tested project
        myFixture.copyDirectoryToProject("", "")
        super.configureFixture(testPath)
    }

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
    override fun getPlatform(): TargetPlatform = JvmPlatforms.jvm8
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}