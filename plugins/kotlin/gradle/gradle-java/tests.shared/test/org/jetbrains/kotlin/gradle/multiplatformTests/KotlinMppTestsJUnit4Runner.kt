// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import kotlin.test.fail

/**
 * Custom test runner to change displayed name of tests to include "parametrization"
 */

class KotlinMppTestsJUnit4Runner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    override fun testName(method: FrameworkMethod): String {
        val test = super.createTest()
        val testNameSuffix: String
        if (test is TestWithKotlinPluginAndGradleVersions) {
            val kotlinVersion = test.kotlinPluginVersion.version
            val gradleVersion = test.testGradleVersion.version
            if (test is TestWithAndroidVersion) {
                val agpVersion = test.agpVersion?.version
                testNameSuffix = "[$kotlinVersion, $gradleVersion, AGP $agpVersion]"
            } else {
                testNameSuffix = "[$kotlinVersion, $gradleVersion]"
            }
        } else {
            fail("${test} must inherit from ${TestWithKotlinPluginAndGradleVersions::class.java.name}")
        }

        return super.testName(method) + testNameSuffix
    }
}
