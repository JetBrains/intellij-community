// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin

import org.jetbrains.kotlin.idea.caches.project.LibraryDependenciesFilter
import org.jetbrains.kotlin.idea.caches.project.LibraryDependenciesFilterUnion
import org.jetbrains.kotlin.idea.caches.project.union
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryDependenciesFilterUnionTest {

    @Test
    fun `result contains union of child results`() {
        val candidates = setOf(
            libraryDependencyCandidate(platform("a")),
            libraryDependencyCandidate(platform("b")),
            libraryDependencyCandidate(platform("c"))
        )

        val filterReturningA = LibraryDependenciesFilter { _, inputCandidates ->
            inputCandidates.filter { it.platform == platform("a") }.toSet()
        }

        val filterReturningB = LibraryDependenciesFilter { _, inputCandidates ->
            inputCandidates.filter { it.platform == platform("b") }.toSet()
        }

        val union = filterReturningA union filterReturningB

        val result = union(platform("arbitrary"), candidates)
        assertEquals(
            setOf(libraryDependencyCandidate(platform("a")), libraryDependencyCandidate(platform("b"))),
            result,
            "Expected filter union to return the union of the results"
        )
    }

    @Test
    fun `union operator produces flat LibraryDependenciesFilterUnion`() {
        val filterA = LibraryDependenciesFilter { _, _ -> emptySet() }
        val filterB = LibraryDependenciesFilter { _, _ -> emptySet() }
        val filterC = LibraryDependenciesFilter { _, _ -> emptySet() }
        val filterD = LibraryDependenciesFilter { _, _ -> emptySet() }

        val union = filterA union filterB union filterC union filterD
        union as LibraryDependenciesFilterUnion

        assertEquals(
            listOf(filterA, filterB, filterC, filterD),
            union.filters,
            "Expected chained invocation of union to produce flat filter union"
        )
    }

    @Test
    fun `cannot instantiate empty filter union`() {
        assertFailsWith<IllegalArgumentException> {
            LibraryDependenciesFilterUnion(emptyList())
        }
    }
}
