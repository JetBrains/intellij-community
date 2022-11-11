// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.caches.project.SharedNativeLibraryToNativeInteropFallbackDependenciesFilter
import org.jetbrains.kotlin.konan.target.KonanTarget.*
import org.junit.Test
import kotlin.test.assertEquals

class SharedNativeLibraryToNativeInteropFallbackDependenciesFilterTest {

    @Test
    fun `select fallback`() {
        val candidates = setOf(
            klibLibraryDependencyCandidate(platform(LINUX_X64), "posix", isInterop = true),
            klibLibraryDependencyCandidate(platform(MACOS_X64), "posix", isInterop = true),
            klibLibraryDependencyCandidate(platform(LINUX_X64, MACOS_X64), "posix", isInterop = true),
        )

        assertEquals(
            setOf(klibLibraryDependencyCandidate(platform(LINUX_X64, MACOS_X64), "posix", isInterop = true)),
            SharedNativeLibraryToNativeInteropFallbackDependenciesFilter(platform(LINUX_X64, MACOS_X64, MINGW_X64), candidates),
            "Expected posix for linux and macos being selected as fallback, because of missing windows target"
        )
    }

    @Test
    fun `select fallback does not work if candidate is _no_ interop`() {
        val candidates = setOf(
            klibLibraryDependencyCandidate(platform(LINUX_X64), "posix", isInterop = false),
            klibLibraryDependencyCandidate(platform(MACOS_X64), "posix", isInterop = false),
            klibLibraryDependencyCandidate(platform(LINUX_X64, MACOS_X64), "posix", isInterop = false),
        )

        assertEquals(
            emptySet(),
            SharedNativeLibraryToNativeInteropFallbackDependenciesFilter(platform(LINUX_X64, MACOS_X64, MINGW_X64), candidates),
            "Expected empty set, because no **interop** fallback candidate is available"
        )
    }

    @Test
    fun `no candidate is selected for native leaf source sets`() {
        val candidates = setOf(
            klibLibraryDependencyCandidate(platform(LINUX_X64), "posix", isInterop = true),
            klibLibraryDependencyCandidate(platform(MACOS_X64), "posix", isInterop = true),
            klibLibraryDependencyCandidate(platform(LINUX_X64, MACOS_X64), "posix", isInterop = true),
        )

        assertEquals(
            emptySet(),
            SharedNativeLibraryToNativeInteropFallbackDependenciesFilter(platform(LINUX_X64), candidates),
            "Expected empty set: No fallbacks required for leaf source sets"
        )
    }

    @Test
    fun `no fallbacks selected for generic source sets`() {
        val candidates = setOf(
            libraryDependencyCandidate(platform("a")),
            libraryDependencyCandidate(platform("b")),
            libraryDependencyCandidate(platform("a", "b"),),
        )

        assertEquals(
            emptySet(),
            SharedNativeLibraryToNativeInteropFallbackDependenciesFilter(platform("a", "b", "c"), candidates),
            "Expected empty set: filter only applies to shared native platforms"
        )
    }

}
