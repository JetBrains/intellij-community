// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.quickfix.tests

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractK2QuickFixTest : AbstractQuickFixTest() {
    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun tearDown() {
        runAll(
          { project.invalidateCaches() },
          { super.tearDown() }
        )
    }

    override val inspectionFileName: String
        get() = ".k2Inspection"

    override fun checkForUnexpectedErrors() {}

    override fun doTest(beforeFileName: String) {
      IgnoreTests.runTestIfNotDisabledByFileDirective(File(beforeFileName).toPath(), IgnoreTests.DIRECTIVES.IGNORE_FIR, "after") {
        super.doTest(beforeFileName)
      }
    }
}