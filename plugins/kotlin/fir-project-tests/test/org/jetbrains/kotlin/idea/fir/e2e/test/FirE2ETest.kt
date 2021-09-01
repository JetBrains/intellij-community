// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.e2e.test

import com.intellij.util.io.isDirectory
import org.jetbrains.kotlin.idea.fir.project.test.base.AbstractFirProjectBasedTests
import org.jetbrains.kotlin.idea.fir.project.test.base.RustProject
import org.jetbrains.kotlin.idea.perf.common.*
import org.jetbrains.kotlin.idea.perf.live.PerformanceTestProfile
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors


class FirE2ETest : AbstractFirProjectBasedTests() {
    override val testPrefix: String = "FIR e2e"
    override val warmUpOnHelloWorldProject: Boolean = false

    fun testRustPlugin() {
        val profile = ProjectBasedTestPreferences(
            warmUpIterations = 0,
            iterations = 1,
            checkForValidity = false,
        )

        test("Rust Plugin", RustProject.project, RustProject.actions, profile)
    }
}