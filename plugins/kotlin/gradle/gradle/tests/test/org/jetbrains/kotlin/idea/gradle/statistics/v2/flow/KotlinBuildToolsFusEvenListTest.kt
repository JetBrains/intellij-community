// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.openapi.application.PathManager
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinBuildToolsFusEvenListTest {
    @Test
    fun checkUniqueEventName() {
        val duplicates = kotlinBuildToolsFusEvenList.groupBy { it.eventName }.filter { it.value.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Found duplicate event names: $duplicates" }
    }

    private val GROUP_EXPECTED_VERSION_AND_HASH = Pair(12, "b310e0ec23e84d8bd9cea557e89a5159")

    @Test
    fun checkGroupVersionVersion() {
        val file = File(PathManager.getCommunityHomePath() + "/plugins/kotlin/gradle/gradle/src/org/jetbrains/kotlin/idea/gradle/statistics/v2/flow/kotlinBuildToolEvents.kt").normalize()
        val actualGroupVersionAndHash =
            Pair(
                KotlinBuildToolFusFlowCollector.group.version,
                calculateFileChecksum(file)
            )
        assertEquals(
            GROUP_EXPECTED_VERSION_AND_HASH,
            actualGroupVersionAndHash,
            "Hash of `${file.absolutePath}` has been changed, please increase FusFlowSendingStep.GROUP_VERSION value. " +
                    "Also you need to update hash and version in this test class."
        )

    }

    private fun calculateFileChecksum(file: File): String {
        assertTrue { file.exists() }
        return MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }
}
