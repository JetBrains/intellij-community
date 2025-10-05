// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.k2FileName
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmWithLibBasicCompletionTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractFirWithLibBasicCompletionTest: AbstractJvmWithLibBasicCompletionTest() {

    override val captureExceptions: Boolean = false

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
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

    override fun fileName(): String = k2FileName(super.fileName(), testDataDirectory, IgnoreTests.FileExtension.FIR)

    override fun executeTest(test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.executeTest(test)
            IgnoreTests.cleanUpIdenticalK2TestFile(dataFile(), IgnoreTests.FileExtension.FIR)
        }
    }
}