// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

abstract class AbstractK2LocalInspectionTest : AbstractLocalInspectionTest() {
    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val inspectionFileName: String = ".k2Inspection"

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun getAfterTestDataAbsolutePath(mainFileName: String): Path {
        val k2FileName = mainFileName.removeSuffix(".kt") + ".k2.kt.after"
        val k2FilePath = testDataDirectory.toPath() / k2FileName
        if (k2FilePath.exists()) return k2FilePath

        return super.getAfterTestDataAbsolutePath(mainFileName)
    }

    override fun doTestFor(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_K2, "after") {
            doTestForInternal(mainFile, inspection, fileText)
        }
    }
}