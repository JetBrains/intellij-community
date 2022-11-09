// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.highlighting.KotlinHighLevelDiagnosticHighlightingPass
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractK2LocalInspectionTest : AbstractLocalInspectionTest() {
    override fun isFirPlugin() = true

    override val inspectionFileName: String = ".k2Inspection"

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun collectHighlightInfos(): List<HighlightInfo> {
        return KotlinHighLevelDiagnosticHighlightingPass.ignoreThisPassInTests { super.collectHighlightInfos() }
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() }
        )
    }

    override fun doTestFor(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_FIR, "after") {
            super.doTestFor(mainFile, inspection, fileText)
        }
    }
}