// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinBuildToolFusMetricTest {

    @Test
    fun testConcatenatedListValuesStringFusMetric() {
        val metric = ConcatenatedAllowedListValuesStringFusMetric("test_metric", listOf("value1", "value4", "value3"))
        val rawValues = listOf(RawFusValue("value1,value2,value3"), RawFusValue("value4, ,value4  "))

        val result = metric.process(rawValues)

        assertNotNull(result)
        assertEquals("value1;value2;value3;value4;value4", result.value)
    }

    @Test
    fun testJoinedListValuesStringFusMetric() {
        val metric = JoinedListValuesStringFusMetric("test_metric")
        val rawValues = listOf(RawFusValue("value1,value2,value3"), RawFusValue("value4, ,value4  "))

        val result = metric.process(rawValues)

        assertNotNull(result)
        assertEquals(listOf("value1", "value2", "value3", "value4", "value4"), result.value)
    }
}
