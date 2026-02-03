// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import org.jetbrains.kotlin.idea.gradle.statistics.v2.flow.KotlinBuildToolFusFlowProcessor
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails

class KotlinBuildToolsFusFlowTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("getFilesToExpectedMetrics")
    fun testFusFlowProcessor(fusProfileFile: List<Path>, buildId: String, expectedFusMetrics: Set<String>, checkExactMatch: Boolean) {
        val aggregatedFusMetric = KotlinBuildToolFusFlowProcessor.aggregateMetricsForBuildId(buildId, fusProfileFile)

        val actualFusMetric = aggregatedFusMetric?.map { "${it.metric.metricRawName}=${it.value}" }?.toSet()
        if (actualFusMetric == null) {
            assertFails { "aggregatedFusMetric should not be null" }
            return
        }

        if (checkExactMatch) {
            assertEquals(
                expectedFusMetrics, actualFusMetric
            )
        } else {
            expectedFusMetrics.forEach {
                assertContains(
                    actualFusMetric,
                    it,
                    "Metric $it is expected for build id: $buildId, but actual metrics are ${actualFusMetric.joinToString()}"
                )
            }

        }
    }


    companion object {
        @JvmStatic
        private fun getFilesToExpectedMetrics() = Stream.of(
            TestData(
                listOf(Path("resources/kotlin-profile/unknown_build.plugin-profile")),
                "unknown_build",
                setOf("BUILD_FINISH_TIME=80000000", "COMPILATION_STARTED=true"),
                true
            ).toArguments("testMetricValidation"),

            TestData(
                listOf(
                    Path("resources/kotlin-profile/build_id_1.plugin-profile"),
                    Path("resources/kotlin-profile/build_id_1_part2.plugin-profile"),
                    Path("resources/kotlin-profile/build_id_2.plugin-profile")
                ),
                "build_id_1",
                setOf("CONFIGURATION_API_COUNT=2"),
                false
            ).toArguments("testAggregateMetrics"),

            TestData(
                listOf(
                    Path("resources/kotlin-profile/build_id.finish-profile"),
                    Path("resources/kotlin-profile/build_id-empty-file.plugin-profile"),
                ),
                "build_id",
                emptySet(),
                true
            ).toArguments("testEmptyFiles"),

            TestData(
                listOf(
                    Path("resources/kotlin-profile/build_id-invalid_metrics.plugin-profile"),
                    Path("resources/kotlin-profile/build_id.finish-profile"),
                ),
                "build_id",
                setOf("PROJECT_PATH=line with special symbols !^&*()_+{\\n}[@#\$%]", "BUILD_FAILED=false", "BUILD_FINISH_TIME=10000000", "BUILD_SRC_EXISTS=false", "CONFIGURATION_API_COUNT=1"),
                true
            ).toArguments("testMetricValidation"),
        )
    }
}

private data class TestData(
    val fusFiles: List<Path>,
    val buildId: String,
    val expectedFusMetrics: Set<String>,
    val checkExactMatch: Boolean = false,
) {
    fun toArguments(name: String): Arguments = Arguments.of(named(name, fusFiles), buildId, expectedFusMetrics, checkExactMatch)
}