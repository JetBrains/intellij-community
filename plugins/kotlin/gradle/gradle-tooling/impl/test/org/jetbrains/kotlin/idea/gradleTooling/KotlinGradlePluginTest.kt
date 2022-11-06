// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling


import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.buildNumber
import org.junit.Test
import kotlin.test.*

class KotlinGradlePluginTest {

    @Test
    fun `parse - sample 180-dev-510`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0-dev-510"))
        assertEquals(version.toKotlinToolingVersion(), KotlinToolingVersion("1.8.0-dev-510"))

        assertEquals(1, version.major)
        assertEquals(8, version.minor)
        assertEquals(0, version.patch)
        assertEquals("1.8.0-dev-510", version.versionString)
    }

    @Test
    fun `compareTo - version string`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertTrue(version < "1.8.10")
        assertTrue(version > "1.7.0")
        assertTrue(version >= "1.8.0")
        assertTrue(version <= "1.8.0")

        assertFailsWith<IllegalArgumentException> { version.compareTo("xxx") }
    }

    @Test
    fun `compareTo - KotlinToolingVersion`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertTrue(version < KotlinToolingVersion("1.8.10"))
        assertTrue(version > KotlinToolingVersion("1.7.0"))
        assertTrue(version >= KotlinToolingVersion("1.8.0"))
        assertTrue(version <= KotlinToolingVersion("1.8.0"))
    }

    @Test
    fun `compareTo - KotlinGradlePluginVersion`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertTrue(version < assertNotNull(KotlinGradlePluginVersion.parse("1.8.10")))
        assertTrue(version > assertNotNull(KotlinGradlePluginVersion.parse("1.7.0")))
        assertTrue(version >= assertNotNull(KotlinGradlePluginVersion.parse("1.8.0")))
        assertTrue(version <= assertNotNull(KotlinGradlePluginVersion.parse("1.8.0")))
    }

    @Test
    fun reparse() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.20-dev-google-3455"))
        assertEquals(3455, version.toKotlinToolingVersion().buildNumber)
        assertNotSame(version, version.reparse())
        assertEquals(version, version.reparse())
    }

    @Test
    fun `invokeWhenAtLeast - with version string`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertNotNull(version.invokeWhenAtLeast("1.7.0") {})
        assertNotNull(version.invokeWhenAtLeast("1.8.0") { })
        assertNull(version.invokeWhenAtLeast("1.9.0") {})
    }

    @Test
    fun `invokeWhenAtLeast - with KotlinToolingVersion`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertNotNull(version.invokeWhenAtLeast(KotlinToolingVersion("1.7.0")) {})
        assertNotNull(version.invokeWhenAtLeast(KotlinToolingVersion("1.8.0")) { })
        assertNull(version.invokeWhenAtLeast(KotlinToolingVersion("1.9.0")) {})
    }

    @Test
    fun `invokeWhenAtLeast - with KotlinGradlePluginVersion`() {
        val version = assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))
        assertNotNull(version.invokeWhenAtLeast(assertNotNull(KotlinGradlePluginVersion.parse("1.7.0"))) {})
        assertNotNull(version.invokeWhenAtLeast(assertNotNull(KotlinGradlePluginVersion.parse("1.8.0"))) { })
        assertNull(version.invokeWhenAtLeast(assertNotNull(KotlinGradlePluginVersion.parse("1.9.0"))) {})
    }
}
