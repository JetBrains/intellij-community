// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.caches.project.HmppSourceModuleDependencyFilter
import org.jetbrains.kotlin.idea.caches.project.HmppSourceModuleDependencyFilter.KlibLibraryGist
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HmppSourceModuleDependencyFilterTest {
    @Test
    fun `any native platform supports stdlib`() {
        val filter = HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64),)

        assertTrue(
            filter.isSupportedDependency(platform(KonanTarget.IOS_X64), KlibLibraryGist(isStdlib = true)),
            "Expected stdlib being supported, even when native targets do not match"
        )

        assertFalse(
            filter.isSupportedDependency(
                platform(KonanTarget.IOS_X64), KlibLibraryGist(isStdlib = false),
            ),
            "Expected non-stdlib not being supported when native targets do not match"
        )
    }

    @Test
    fun `native unspecified target supports stdlib`() {
        val filter = HmppSourceModuleDependencyFilter(platform(NativePlatformUnspecifiedTarget))

        assertTrue(
            filter.isSupportedDependency(
                platform(KonanTarget.IOS_X64), KlibLibraryGist(isStdlib = true)
            ),
            "Expected stdlib being supported, even when native targets do not match"
        )

        assertTrue(
            filter.isSupportedDependency(
                platform(NativePlatformUnspecifiedTarget), KlibLibraryGist(isStdlib = true)
            ),
            "Expected stdlib being supported, even when native targets do not match"
        )
    }

    @Test
    fun `incompatible backends are not supported`() {
        val filter = HmppSourceModuleDependencyFilter(JvmPlatforms.jvm8)

        assertFalse(
            filter.isSupportedDependency(JsPlatforms.defaultJsPlatform),
            "Expected jvm -> js to be incompatible"
        )

        assertFalse(
            filter.isSupportedDependency(TargetPlatform(setOf(NativePlatformUnspecifiedTarget))),
            "Expected jvm -> native to be incompatible"
        )

        assertFalse(
            filter.isSupportedDependency(platform(KonanTarget.IOS_X64)),
            "Expected jvm -> native(iosX64) to be incompatible"
        )

        assertFalse(
            filter.isSupportedDependency(platform(KonanTarget.MACOS_X64, KonanTarget.LINUX_X64)),
            "Expected jvm -> native(macosX64, linuxX64) to be incompatible"
        )
    }

    @Test
    fun `jvm16 is supported for jvm18`() {
        val filter = HmppSourceModuleDependencyFilter(JvmPlatforms.jvm8,)

        assertTrue(
            filter.isSupportedDependency(JvmPlatforms.jvm6),
            "Expected jvm18 -> jvm16 to be supported"
        )
    }

    /**
     * [COUNTER INTUITIVE]: This might not make sense from the build systems perspective, but we still want to allow
     * analyzing source code with jm16 together with jvm18.
     *
     * See: https://github.com/JetBrains/kotlin/blob/a101d12c780060f966b47b69bbd1ea4185910b8e/compiler/config.jvm/src/org/jetbrains/kotlin/platform/jvm/JvmPlatform.kt#L63
     * for further details. Since that revision, the platforms will be considered equal even for different jvm targets.
     * This test will probably fail, when the JvmPlatform takes the jvm target into account.
     */
    @Test
    fun `jvm18 is supported for jvm16`() {
        val filter = HmppSourceModuleDependencyFilter(JvmPlatforms.jvm6)

        assertTrue(
            filter.isSupportedDependency(JvmPlatforms.jvm8),
            "Expected jvm16 -> jvm18 to be supported"
        )
    }

    @Test
    fun `same native targets are supported`() {
        assertTrue(
            HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64))
                .isSupportedDependency(platform(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64)),
            "Expected same konan targets to be supported"
        )

        assertTrue(
            HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64))
                .isSupportedDependency(platform(KonanTarget.LINUX_X64)),
            "Expected same konan targets to be supported"
        )
    }

    @Test
    fun `non same konan leaf targets are not supported`() {
        assertFalse(
            HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64))
                .isSupportedDependency(platform(KonanTarget.MACOS_X64)),
            "Expected linuxX64 -> macosX64 to be unsupported"
        )
    }

    @Test
    fun `any shared native target is compatible with any other _KLIB_ shared native`() {
        assertTrue(
            HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64))
                .isSupportedDependency(platform(KonanTarget.LINUX_X64, KonanTarget.MINGW_X64), klibLibraryGist = KlibLibraryGist(false)),
            "Expected any shared native platform being supported by any other shared native klib"
        )
    }

    @Test
    fun `any shared native target is _not_ compatible with other _non klib_ shared native`() {
        assertFalse(
            HmppSourceModuleDependencyFilter(platform(KonanTarget.LINUX_X64, KonanTarget.MACOS_X64))
                .isSupportedDependency(platform(KonanTarget.LINUX_X64, KonanTarget.MINGW_X64), klibLibraryGist = null),
            "Expected any shared native platform being not supported by non target matching non klib library"
        )
    }
}
