// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractMultiFileLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import kotlin.io.path.Path

abstract class AbstractK2MultiFileLocalInspectionTest: AbstractMultiFileLocalInspectionTest() {
    override fun isFirPlugin() = true

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun inspectionClassFieldName(): String = "k2InspectionClass"

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun doTest(pathInString: String) {
        val path = Path(pathInString)
        IgnoreTests.runTestIfNotDisabledByFileDirective(path, IgnoreTests.DIRECTIVES.IGNORE_K2, "after") {
            super.doTest(pathInString)
        }
    }
}