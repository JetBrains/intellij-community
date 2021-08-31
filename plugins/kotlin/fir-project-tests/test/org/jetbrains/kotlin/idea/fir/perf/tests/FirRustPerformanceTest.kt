// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.perf.tests

import org.jetbrains.kotlin.idea.fir.project.test.base.AbstractFirProjectBasedTests
import org.jetbrains.kotlin.idea.fir.project.test.base.RustProject
import org.jetbrains.kotlin.idea.perf.live.PerformanceTestProfile

class FirRustPerformanceTest: AbstractFirProjectBasedTests() {
    override val testPrefix: String = "FIR"
    override val warmUpOnHelloWorldProject: Boolean = true

    fun testRustPlugin() {
        val profile = PerformanceTestProfile(
            warmUpIterations = 5,
            iterations = 10,
        )

        test("Rust Plugin", RustProject.project, RustProject.actions, profile)
    }
}