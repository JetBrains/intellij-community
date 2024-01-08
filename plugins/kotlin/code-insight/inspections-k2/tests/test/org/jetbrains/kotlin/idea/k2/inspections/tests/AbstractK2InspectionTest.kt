// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import kotlin.io.path.Path

abstract class AbstractK2InspectionTest : AbstractInspectionTest() {
    override fun isFirPlugin() = true
    override fun inspectionClassDirective() = "// K2_INSPECTION_CLASS:"
    override fun registerGradlPlugin() {}

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun doTest(path: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(Path(path), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(path)
        }
    }

    override fun tearDown() {
        runAll(
          { project.invalidateCaches() },
          { super.tearDown() }
        )
    }
}