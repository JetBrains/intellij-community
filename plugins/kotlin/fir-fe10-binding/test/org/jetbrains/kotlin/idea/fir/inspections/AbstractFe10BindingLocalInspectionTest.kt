// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.kotlin.idea.fir.highlighter.KotlinHighLevelDiagnosticHighlightingPass
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractFe10BindingLocalInspectionTest : AbstractLocalInspectionTest() {
    override fun isFirPlugin() = true

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun collectHighlightInfos(): List<HighlightInfo> {
        return KotlinHighLevelDiagnosticHighlightingPass.ignoreThisPassInTests { super.collectHighlightInfos() }
    }

    override fun doTestFor(mainFile: File, inspection: AbstractKotlinInspection, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_FE10_BINDING_BY_FIR, "after") {
            super.doTestFor(mainFile, inspection, fileText)
        }
    }
}