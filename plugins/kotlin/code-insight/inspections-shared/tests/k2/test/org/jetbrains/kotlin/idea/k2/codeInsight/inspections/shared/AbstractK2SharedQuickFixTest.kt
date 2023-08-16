// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.utils.IgnoreTests
import kotlin.io.path.Path

abstract class AbstractK2SharedQuickFixTest : AbstractQuickFixTest() {
    override fun checkForUnexpectedErrors() {}

    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          { project.invalidateCaches() },
          { super.tearDown() }
        )
    }

    override fun doTest(beforeFileName: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Path(beforeFileName), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(beforeFileName)
        }
    }
}