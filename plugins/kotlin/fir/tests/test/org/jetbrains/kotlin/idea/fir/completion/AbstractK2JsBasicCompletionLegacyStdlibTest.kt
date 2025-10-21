// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.AbstractJSBasicCompletionTestBase
import org.jetbrains.kotlin.idea.fir.completion.SerializabilityChecker
import org.jetbrains.kotlin.idea.test.KotlinStdJSLegacyCombinedJarProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractK2JsBasicCompletionLegacyStdlibTest : AbstractJSBasicCompletionTestBase() {
    override val captureExceptions: Boolean = false

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, k2Extension = IgnoreTests.FileExtension.FIR)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalK2TestFile(dataFile(), k2Extension = IgnoreTests.FileExtension.FIR)
        }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun extraLookupElementCheck(lookupElement: LookupElement) {
        SerializabilityChecker.checkLookupElement(lookupElement, myFixture.project)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinStdJSLegacyCombinedJarProjectDescriptor
    }
}
