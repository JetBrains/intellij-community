// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.junit.Test
import kotlin.test.*

class IdeKotlinVersionTest {
    @Test
    fun testReleaseVersion() {
        fun test(version: String, withBuildNumber: Boolean, artifactBuildSuffix: String = "") = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 5, 10), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Release, kind)
            assertEquals(LanguageVersion.KOTLIN_1_5, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_5, apiVersion)
            assertEquals("1.5.10", baseVersion)
            assertEquals("1.5.10$artifactBuildSuffix", artifactVersion)
            assertTrue(isRelease)
            assertFalse(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
            if (withBuildNumber) assertNotNull(buildNumber) else assertNull(buildNumber)
        }

        test("1.5.10", withBuildNumber = false)
        test("1.5.10-235", withBuildNumber = true, artifactBuildSuffix = "-235")
        test("1.5.10-release", withBuildNumber = false)
        test("1.5.10-release-123", withBuildNumber = true)
        test("1.5.10-Release-1", withBuildNumber = true)
    }

    @Test
    fun testReleaseCandidateVersion() {
        fun test(version: String, withBuildNumber: Boolean = false, artifactBuildSuffix: String = "") = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 6, 0), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.ReleaseCandidate(1), kind)
            assertEquals(LanguageVersion.KOTLIN_1_6, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_6, apiVersion)
            assertEquals("1.6.0", baseVersion)
            assertEquals("1.6.0-RC$artifactBuildSuffix", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
            if (withBuildNumber) assertNotNull(buildNumber) else assertNull(buildNumber)
        }

        test("1.6.0-RC")
        test("1.6.0-RC-55", withBuildNumber = true, artifactBuildSuffix = "-55")
        test("1.6.0-RC-release")
        test("1.6.0-RC-release-123", withBuildNumber = true)
    }

    @Test
    fun testReleaseCandidate2Version() {
        fun test(version: String, artifactBuildSuffix: String = "") = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 6, 20), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.ReleaseCandidate(2), kind)
            assertEquals(LanguageVersion.KOTLIN_1_6, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_6, apiVersion)
            assertEquals("1.6.20", baseVersion)
            assertEquals("1.6.20-RC2$artifactBuildSuffix", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
        }

        test("1.6.20-RC2")
        test("1.6.20-RC2-44", artifactBuildSuffix = "-44")
        test("1.6.20-RC2-release")
        test("1.6.20-RC2-release-123")
    }

    @Test
    fun testMilestoneVersion() {
        fun test(version: String, milestone: Int) = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 5, 30), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Milestone(milestone), kind)
            assertEquals(LanguageVersion.KOTLIN_1_5, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_5, apiVersion)
            assertEquals("1.5.30", baseVersion)
            assertEquals("1.5.30-M${milestone}", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
        }

        test("1.5.30-M1", milestone = 1)
        test("1.5.30-M2-release", milestone = 2)
        test("1.5.30-M2-release-123", milestone = 2)
        test("1.5.30-M15-release-123", milestone = 15)
    }

    @Test
    fun testEapVersion() {
        fun test(version: String, eapNumber: Int) = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 5, 30), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Eap(eapNumber), kind)
            assertEquals(LanguageVersion.KOTLIN_1_5, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_5, apiVersion)
            assertEquals("1.5.30", baseVersion)
            assertEquals("1.5.30-eap${if (eapNumber == 1) "" else eapNumber.toString()}", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
        }

        test("1.5.30-eap", eapNumber = 1)
        test("1.5.30-eap2-release", eapNumber = 2)
        test("1.5.30-eap2-release-123", eapNumber = 2)
        test("1.5.30-eap15-release-123", eapNumber = 15)
    }

    @Test
    fun testBetaVersion() {
        fun test(version: String, beta: Int, artifactBuildSuffix: String = "") = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 5, 0), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Beta(beta), kind)
            assertEquals(LanguageVersion.KOTLIN_1_5, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_5, apiVersion)
            assertEquals("1.5.0", baseVersion)
            assertEquals(if (beta == 1) "1.5.0-Beta" else "1.5.0-Beta$beta$artifactBuildSuffix", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertFalse(isSnapshot)
        }

        test("1.5.0-Beta", beta = 1)
        test("1.5.0-Beta2-release", beta = 2)
        test("1.5.0-BETA5-123", beta = 5, artifactBuildSuffix = "-123")
        test("1.5.0-beta15-release-123", beta = 15)
    }

    @Test
    fun testDevVersion() {
        fun test(version: String, artifactBuildSuffix: String) = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 6, 10), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Dev, kind)
            assertEquals(LanguageVersion.KOTLIN_1_6, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_6, apiVersion)
            assertEquals("1.6.10", baseVersion)
            assertEquals("1.6.10-dev${artifactBuildSuffix}", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertTrue(isDev)
            assertFalse(isSnapshot)
        }

        test("1.6.10-dev", artifactBuildSuffix = "")
        test("1.6.10-dev-12", artifactBuildSuffix = "-12")
    }

    @Test
    fun testSnapshotVersion() {
        fun test(version: String, artifactBuildSuffix: String) = with (IdeKotlinVersion.get(version)) {
            assertEquals(version, rawVersion)
            assertEquals(KotlinVersion(1, 0, 0), kotlinVersion)
            assertEquals(IdeKotlinVersion.Kind.Snapshot, kind)
            assertEquals(LanguageVersion.KOTLIN_1_0, languageVersion)
            assertEquals(ApiVersion.KOTLIN_1_0, apiVersion)
            assertEquals("1.0.0", baseVersion)
            assertEquals("1.0.0-SNAPSHOT${artifactBuildSuffix}", artifactVersion)
            assertFalse(isRelease)
            assertTrue(isPreRelease)
            assertFalse(isDev)
            assertTrue(isSnapshot)
        }

        test("1.0.0-snapshot", artifactBuildSuffix = "")
        test("1.0.0-SNAPSHOT", artifactBuildSuffix = "")
        test("1.0.0-SNAPSHOT-20", artifactBuildSuffix = "-20")
    }

    @Test
    fun testStableVersionWithoutBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20").getOrThrow()
        assertEquals(version, version.withoutBuildNumber())
    }

    @Test
    fun testStableVersionWithBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20").getOrThrow()
        val versionWithNumber = IdeKotlinVersion.parse("1.8.20-585").getOrThrow()
        assertEquals(version, versionWithNumber.withoutBuildNumber())
        assertEquals("1.8.20", versionWithNumber.withoutBuildNumber().artifactVersion)
    }

    @Test
    fun testBetaVersionWithoutBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-Beta").getOrThrow()
        assertEquals(version, version.withoutBuildNumber())
    }

    @Test
    fun testBetaVersionWithBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-Beta").getOrThrow()
        val versionWithNumber = IdeKotlinVersion.parse("1.8.20-Beta-585").getOrThrow()
        assertEquals(version, versionWithNumber.withoutBuildNumber())
    }

    @Test
    fun testRCVersionWithoutBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-RC2").getOrThrow()
        assertEquals(version, version.withoutBuildNumber())
    }

    @Test
    fun testRCVersionWithBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-RC2").getOrThrow()
        val versionWithNumber = IdeKotlinVersion.parse("1.8.20-RC2-585").getOrThrow()
        assertEquals(version, versionWithNumber.withoutBuildNumber())
    }

    @Test
    fun testReleaseVersionWithoutBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-release").getOrThrow()
        assertEquals(version, version.withoutBuildNumber())
        assertEquals("1.8.20", version.artifactVersion)
    }

    @Test
    fun testReleaseVersionWithBuildNumber() {
        val version = IdeKotlinVersion.parse("1.8.20-release").getOrThrow()
        val versionWithNumber = IdeKotlinVersion.parse("1.8.20-release-585").getOrThrow()
        assertEquals(version, versionWithNumber.withoutBuildNumber())
        assertEquals("1.8.20", versionWithNumber.withoutBuildNumber().artifactVersion)
    }
}