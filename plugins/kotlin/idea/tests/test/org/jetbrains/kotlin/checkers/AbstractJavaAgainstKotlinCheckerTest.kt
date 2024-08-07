// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.checkers

import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.io.File
import java.nio.file.Path

abstract class AbstractJavaAgainstKotlinSourceCheckerTest : AbstractJavaAgainstKotlinCheckerTest() {
    fun doTest(path: String) {
        val relativePath = File(path).toRelativeString(File(testDataPath))
        fun doTest() {
            IgnoreTests.runTestIfNotDisabledByFileDirective(Path.of(path), IgnoreTests.DIRECTIVES.of(pluginMode), directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE) {
                doTest(true, true, relativePath.replace(".kt", ".java"), relativePath)
            }
        }

        val configFile = configFileText
        if (configFile != null) {
            withCustomCompilerOptions(configFile, project, module) {
                doTest()
            }
        } else {
            doTest()
        }
    }
}
