// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinSerializedDependencyPool
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IdeaKotlinSerializedDependencyPoolTest {

    @Test
    fun storesEqualDependenciesOnce() {
        val pool = IdeaKotlinSerializedDependencyPool(
            mapOf(
                "commonMain" to listOf(bytes(1), bytes(2)),
                "iosMain" to listOf(bytes(1), bytes(2), bytes(3)),
                "iosArm64Main" to listOf(bytes(1), bytes(3)),
            )
        )

        assertEquals(3, pool.dependencies.size, "Expected one pool entry for each distinct dependency")
    }

    @Test
    fun keepsTheClasspathOrderOfEachSourceSet() {
        val dependenciesBySourceSet = mapOf(
            "commonMain" to listOf(bytes(3), bytes(1)),
            "iosMain" to listOf(bytes(1), bytes(2), bytes(3)),
            "emptyMain" to emptyList(),
        )

        val restored = IdeaKotlinSerializedDependencyPool(dependenciesBySourceSet).toDependenciesBySourceSet()

        assertEquals(dependenciesBySourceSet.keys, restored.keys)
        for ((sourceSetName, expected) in dependenciesBySourceSet) {
            val actual = restored.getValue(sourceSetName)
            assertEquals(expected.size, actual.size, "Unexpected dependency count of $sourceSetName")
            expected.zip(actual).forEach { (expectedBytes, actualBytes) -> assertContentEquals(expectedBytes, actualBytes) }
        }
    }

    @Test
    fun sourceSetsShareTheRestoredInstances() {
        val restored = IdeaKotlinSerializedDependencyPool(
            mapOf(
                "commonMain" to listOf(bytes(1)),
                "iosMain" to listOf(bytes(1)),
            )
        ).toDependenciesBySourceSet()

        assertSame(restored.getValue("commonMain").single(), restored.getValue("iosMain").single())
    }

    private fun bytes(seed: Int): ByteArray = ByteArray(16) { seed.toByte() }
}
