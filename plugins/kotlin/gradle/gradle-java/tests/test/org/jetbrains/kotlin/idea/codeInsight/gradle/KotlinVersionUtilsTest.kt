// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.junit.Test
import kotlin.test.*

class KotlinVersionUtilsTest {

    @Test
    fun exactRequirement() {
        assertTrue(
            parseKotlinVersionRequirement("1.5.0").matches("1.5.0"),
        )

        assertFalse(
            parseKotlinVersionRequirement("1.5.0").matches("1.5.0-RC"),
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.0-RC1").matches("1.5.0-rc1")
        )
    }

    @Test
    fun sameVersionOrHigher() {
        assertTrue(
            parseKotlinVersionRequirement("1.4.0+").matches("1.4.0")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0+").matches("1.4.10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-M1+").matches("1.4.0-M1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-M1+").matches("1.4.0-M2")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-M1+").matches("1.4.0-RC")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.4.0-RC2+").matches("1.4.0-RC1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-RC2+").matches("1.4.0-RC3")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-RC3+").matches("1.4.0-rc4")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0-RC3+").matches("1.4.0-rc4-100")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.5.10+").matches("1.5.0-rc")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.5.10+").matches("1.5.0")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5+").matches("1.5.0")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5+").matches("1.5.10")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.5+").matches("1.5.0-rc")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20-M1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-M1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20-dev-39")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-snapshot+").matches("1.5.20-dev-39")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-snapshot+").matches("1.5.20-dev39")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20-dev39")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-SNAPSHOT")
        )
    }

    @Test
    fun ranges() {
        assertTrue(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.4.0")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.4.10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.4.20-M2")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.4.0-rc")
        )

        assertFalse(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.5.10-rc1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.5.0-alpha1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.4.0 <=> 1.5.0").matches("1.5.0-rc")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-snapshot+").matches("1.5.20-dev-10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20-dev-10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-snapshot+").matches("1.5.20-dev10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev-0+").matches("1.5.20-dev10")
        )
    }

    @Test
    fun wildcard() {
        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() > parseKotlinVersion("1.5.22")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() > parseKotlinVersion("1.5")
        )

        assertTrue(
            parseKotlinVersion("1.5.31").toWildcard() > parseKotlinVersion("1.5.30")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-rc")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-alpha1")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-M1")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-dev-42")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-dev-1")
        )

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-snapshot")
        )
    }

    @Test
    fun compareClassifierNumberAndBuildNumber() {
        assertTrue(
            parseKotlinVersion("1.6.20-M1") < parseKotlinVersion("1.6.20")
        )

        assertTrue(
            parseKotlinVersion("1.6.20") > parseKotlinVersion("1.6.20-1")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-1") < parseKotlinVersion("1.6.20-2")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-M1") < parseKotlinVersion("1.6.20-M2")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-M1-2") > parseKotlinVersion("1.6.20-M1-1")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-M1-2") < parseKotlinVersion("1.6.20-M2-1")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-M1-2") < parseKotlinVersion("1.6.20-M2")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-beta1") > parseKotlinVersion("1.6.20-beta")
        )

        assertTrue(
            parseKotlinVersion("1.6.20-M1") > parseKotlinVersion("1.6.20-M1-1")
        )
    }

    @Test
    fun maturityWithClassifierNumberAndBuildNumber() {
        assertEquals(
            KotlinVersionMaturity.STABLE,
            parseKotlinVersion("1.6.20").maturity
        )

        assertEquals(
            KotlinVersionMaturity.STABLE,
            parseKotlinVersion("1.6.20-999").maturity
        )

        assertEquals(
            KotlinVersionMaturity.STABLE,
            parseKotlinVersion("1.6.20-release-999").maturity
        )

        assertEquals(
            KotlinVersionMaturity.STABLE,
            parseKotlinVersion("1.6.20-rElEaSe-999").maturity
        )

        assertEquals(
            KotlinVersionMaturity.RC,
            parseKotlinVersion("1.6.20-rc2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.RC,
            parseKotlinVersion("1.6.20-RC2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.BETA,
            parseKotlinVersion("1.6.20-beta2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.BETA,
            parseKotlinVersion("1.6.20-bEtA2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.ALPHA,
            parseKotlinVersion("1.6.20-alpha2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.ALPHA,
            parseKotlinVersion("1.6.20-aLpHa2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.MILESTONE,
            parseKotlinVersion("1.6.20-m2411-1901").maturity
        )

        assertEquals(
            KotlinVersionMaturity.MILESTONE,
            parseKotlinVersion("1.6.20-M2411-1901").maturity
        )
    }

    @Test
    fun invalidMilestoneVersion() {
        val exception = assertFailsWith<IllegalArgumentException> { parseKotlinVersion("1.6.20-M") }
        assertTrue("maturity" in exception.message.orEmpty().lowercase(), "Expected 'maturity' issue mentioned in error message")
    }

    @Test
    fun buildNumber() {
        assertEquals(510, parseKotlinVersion("1.6.20-510").buildNumber)
        assertEquals(510, parseKotlinVersion("1.6.20-release-510").buildNumber)
        assertEquals(510, parseKotlinVersion("1.6.20-rc1-510").buildNumber)
        assertEquals(510, parseKotlinVersion("1.6.20-beta1-510").buildNumber)
        assertEquals(510, parseKotlinVersion("1.6.20-alpha1-510").buildNumber)
        assertEquals(510, parseKotlinVersion("1.6.20-m1-510").buildNumber)
    }

    @Test
    fun classifierNumber() {
        assertEquals(2, parseKotlinVersion("1.6.20-rc2-510").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-beta2-510").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-alpha2-510").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-m2-510").classifierNumber)

        assertEquals(2, parseKotlinVersion("1.6.20-rc2").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-beta2").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-alpha2").classifierNumber)
        assertEquals(2, parseKotlinVersion("1.6.20-m2").classifierNumber)
    }
}
