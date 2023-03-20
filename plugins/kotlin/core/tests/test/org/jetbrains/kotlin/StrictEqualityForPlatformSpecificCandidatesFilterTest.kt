// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.base.analysis.libraries.LibraryDependencyCandidate
import org.jetbrains.kotlin.idea.caches.project.StrictEqualityForPlatformSpecificCandidatesFilter
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.Test
import kotlin.test.assertEquals

class StrictEqualityForPlatformSpecificCandidatesFilterTest {
    private val aCandidate = libraryDependencyCandidate(platform("a"))
    private val abCandidate = libraryDependencyCandidate(platform("a", "b"))
    private val abcCandidate = libraryDependencyCandidate(platform("a", "b", "c"))

    private val candidates =
        setOf(aCandidate, abCandidate, abcCandidate)

    @Test
    fun `requires strict equality for platform-specific libraries`() {
        platform("a").shouldSee(aCandidate)
        platform("b").shouldSeeNothing()
        platform("c").shouldSeeNothing()
        platform("unknown").shouldSeeNothing()
    }

    @Test
    fun `allows subsets for non-platform-specific libraries`() {
        platform("a", "b").shouldSee(abCandidate, abcCandidate)
        platform("a" ,"b", "c").shouldSee(abcCandidate)
        platform("a" , "b", "c", "d").shouldSeeNothing()
    }

    private fun TargetPlatform.shouldSee(vararg expectedCandidates: LibraryDependencyCandidate) {
        assertEquals(
            expectedCandidates.toSet(),
            StrictEqualityForPlatformSpecificCandidatesFilter(this, candidates)
        )
    }

    private fun TargetPlatform.shouldSeeNothing() = shouldSee()
}
