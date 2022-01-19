// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            parseKotlinVersionRequirement("1.4.0-RC3+").matches("1.4.0-rc-4")
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
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-M1")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-dev-39")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-dev39")
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
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-dev-10")
        )

        assertTrue(
            parseKotlinVersionRequirement("1.5.20-dev+").matches("1.5.20-dev10")
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

        assertTrue(
            parseKotlinVersion("1.5.30").toWildcard() < parseKotlinVersion("1.5.30-unknown")
        )
    }
}
