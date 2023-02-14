// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.caches.project.DefaultLibraryDependenciesFilter
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget.LINUX_X64
import org.jetbrains.kotlin.konan.target.KonanTarget.MACOS_X64
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
import org.junit.Test
import kotlin.test.assertEquals

class DefaultLibraryDependenciesFilterTest {

    @Test
    fun `generic test platforms`() {
        val candidates = setOf(
            libraryDependencyCandidate(platform("a")),
            libraryDependencyCandidate(platform("b")),
            libraryDependencyCandidate(platform("c")),
            libraryDependencyCandidate(platform("a", "b")),
            libraryDependencyCandidate(platform("a", "c")),
            libraryDependencyCandidate(platform("c", "d")),
            libraryDependencyCandidate(platform("a", "b", "c"))
        )

        assertEquals(
            emptySet(),
            DefaultLibraryDependenciesFilter(platform("z"), candidates),
            "Expected empty set: No candidates support unknown platform z"
        )

        assertEquals(
            setOf(
                libraryDependencyCandidate(platform("a")),
                libraryDependencyCandidate(platform("a", "b")),
                libraryDependencyCandidate(platform("a", "c")),
                libraryDependencyCandidate(platform("a", "b", "c"))
            ),
            DefaultLibraryDependenciesFilter(platform("a"), candidates),
            "Expected all candidates that support a"
        )

        assertEquals(
            setOf(
                libraryDependencyCandidate(platform("a", "b")),
                libraryDependencyCandidate(platform("a", "b", "c"))
            ),
            DefaultLibraryDependenciesFilter(platform("a", "b"), candidates),
            "Expected all candidates that support a and b"
        )

        assertEquals(
            emptySet(),
            DefaultLibraryDependenciesFilter(platform("a", "z"), candidates),
            "Expected empty set: No candidates support unknown platform z"
        )
    }

    @Test
    fun `native platforms`() {
        val candidates = setOf(
            // generic non-native candidates
            libraryDependencyCandidate(platform("a")),
            libraryDependencyCandidate(platform("b")),
            libraryDependencyCandidate(platform("a", "b")),

            // native candidates
            libraryDependencyCandidate(platform(NativePlatformUnspecifiedTarget)),
            libraryDependencyCandidate(platform(LINUX_X64)),
            libraryDependencyCandidate(platform(MACOS_X64)),
            libraryDependencyCandidate(platform(LINUX_X64, MACOS_X64))
        )

        assertEquals(
            setOf(
                libraryDependencyCandidate(platform("a")),
                libraryDependencyCandidate(platform("a", "b")),
            ),
            DefaultLibraryDependenciesFilter(platform("a"), candidates),
            "Expected all candidates supporting platform a"
        )

        assertEquals(
            emptySet(),
            DefaultLibraryDependenciesFilter(platform("z"), candidates),
            "Expected empty set: No candidates support unknown platform z"
        )

        assertEquals(
            setOf(libraryDependencyCandidate(platform(NativePlatformUnspecifiedTarget))),
            DefaultLibraryDependenciesFilter(platform(KonanTarget.MINGW_X64), candidates),
            "Expected NativePlatformUnspecifiedTarget for leaf native platform"
        )

        assertEquals(
            setOf(
                libraryDependencyCandidate(platform(NativePlatformUnspecifiedTarget)),
                libraryDependencyCandidate(platform(LINUX_X64, MACOS_X64)),
                libraryDependencyCandidate(platform(LINUX_X64))
            ),
            DefaultLibraryDependenciesFilter(platform(LINUX_X64), candidates),
            "Expected all platforms supporting linux"
        )
    }
}
