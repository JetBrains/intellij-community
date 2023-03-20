// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.test

import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePluginVersion
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class CompatibilityVerifierVersionComparisonTest : LightPlatformTestCase() {
    fun testValidVersion() {
        testVersion("213-1.6.21-release-334-IJ6777.52", "1.6.21-release-334", isAndroidStudio = false, "2021.3")
        testVersion("213-1.6.20-RC-180-IJ6777.52", "1.6.20-RC-180", isAndroidStudio = false, "2021.3")
        testVersion("221-1.6.20-M1-release-207-AC4501.59", "1.6.20-M1-release-207", isAndroidStudio = false, "2022.1")
        testVersion("203-1.4.20-dev-4575-IJ1234.45-1", "1.4.20-dev-4575", isAndroidStudio = false, "2020.3")

        testVersion("213-1.6.21-release-334-AS6777.52", "1.6.21-release-334", isAndroidStudio = true, "2021.3")
        testVersion("211-1.4.30-release-AS193", "1.4.30-release", isAndroidStudio = true, "2021.1")
        testVersion("202-1.4.30-AS", "1.4.30", isAndroidStudio = true, "2020.2")

        testVersion("212-1.6.20-dev-4868-IJSNAPSHOT", "1.6.20-dev-4868", isAndroidStudio = false, "2021.2")
    }

    fun testInvalidVersion() {
        testInvalidVersion("203-1.4.20-dev-M5-4575-IJ1234.45-1")
        testInvalidVersion("203-1.4.20-IK.45")
        testInvalidVersion("203-1.4.20-1234.45")
        testInvalidVersion("1.4-release-M5-IJ2020.1-1")
    }

    private fun testVersion(rawVersion: String, expectedKotlinVersion: String, isAndroidStudio: Boolean, expectedPlatformVersion: String) {
        val version = KotlinIdePluginVersion.parse(rawVersion).getOrThrow()
        assertEquals(expectedKotlinVersion, version.kotlinCompilerVersion.rawVersion)
        assertEquals(isAndroidStudio, version.isAndroidStudio)
        assertEquals(expectedPlatformVersion, version.platformVersion)
    }

    private fun testInvalidVersion(version: String) {
        assertTrue("Version \"$version\" was parsed successfully", KotlinIdePluginVersion.parse(version).isFailure)
    }
}