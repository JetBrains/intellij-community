// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.gradle.statistics.GradleStatisticsEventGroups
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

    @Test
    fun checkEveryEventGroupHasSendingStep() {
        val declaredEventNames = kotlinBuildToolsFusEvenList.map { it.eventName }
        val missing = GradleStatisticsEventGroups.entries.filter { it !in declaredEventNames }
        assert(missing.isEmpty()) {
            "No event is register for for the event groups: $missing. Please add corresponding FusFlowSendingStep to `kotlinBuildToolsFusEvenList`"
        }
    }

    private val GROUP_EXPECTED_VERSION_AND_HASH = Pair(21, "60de8bc5f268a3a84150fb39c8060f1f")

    /**
     * The source files that define the reported events and the event metrics.
     * A change in any of them needs a new group version.
     * The order is a part of the checksum, so keep it stable.
     */
    private val VERSIONED_SOURCE_FILE_NAMES = listOf(
        "kotlinBuildToolEvents.kt",
        "KotlinBuildToolFusMetric.kt",
    )

    @Test
    fun checkGroupVersionVersion() {
        val files = VERSIONED_SOURCE_FILE_NAMES.map { versionedSourceFile(it) }
        val actualGroupVersionAndHash =
            Pair(
                KotlinBuildToolFusFlowCollector.group.version,
                calculateFilesChecksum(files)
            )
        assertEquals(
            GROUP_EXPECTED_VERSION_AND_HASH,
            actualGroupVersionAndHash,
            "Hash of ${files.joinToString { "`${it.absolutePath}`" }} has been changed, " +
                    "please increase KotlinBuildToolFusFlowCollector.GROUP_VERSION value. " +
                    "Also you need to update hash and version in this test class."
        )

    }

    private fun versionedSourceFile(fileName: String): File =
        File(PathManager.getCommunityHomePath() + "/plugins/kotlin/gradle/gradle/src/org/jetbrains/kotlin/idea/gradle/statistics/v2/flow/" + fileName).normalize()

    private fun calculateFilesChecksum(files: List<File>): String {
        val digest = MessageDigest.getInstance("MD5")
        for (file in files) {
            assertTrue(file.exists(), "File `${file.absolutePath}` does not exist")
            digest.update(file.readBytes())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
