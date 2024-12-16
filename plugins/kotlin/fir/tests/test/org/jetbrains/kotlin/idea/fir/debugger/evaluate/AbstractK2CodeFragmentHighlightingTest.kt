// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.debugger.evaluate

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.debugger.evaluate.AbstractCodeFragmentHighlightingTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import java.nio.file.Paths

abstract class AbstractK2CodeFragmentHighlightingTest : AbstractCodeFragmentHighlightingTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() },
        )
    }

    override fun doTest(filePath: String) {
        runTestIfNotDisabledByFileDirective(filePath) {
            super.doTest(filePath)
        }
    }

    override fun doTestWithImport(filePath: String) {
        runTestIfNotDisabledByFileDirective(filePath) {
            super.doTestWithImport(filePath)
        }
    }

    private fun runTestIfNotDisabledByFileDirective(filePath: String, test: () -> Unit) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(filePath),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            test()
        }
    }
}
