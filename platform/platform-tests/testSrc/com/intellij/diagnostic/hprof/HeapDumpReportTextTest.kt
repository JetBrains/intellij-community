// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof

import com.intellij.diagnostic.hprof.util.HeapReportUtils.sectionHeader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HeapDumpReportTextTest {

  @Test
  fun `includes heap statistics, report reason, and platform statistics`() {
    val reportText = """
      =================== HEAP SUMMARY ==================
      Class count: 1
    """.trimIndent()
    val heapStats = """
      Maximum heap size: 2.14GB
      Committed heap size: 1.07GB
      Free heap size: 512MB
    """.trimIndent()
    val liveStats = """
      Projects open: 1
      Project 1:
    """.trimIndent()

    val report = invokeGetHeapDumpReportText(
      reportText,
      reason = "OutOfMemory",
      liveStats = liveStats,
      heapStats = heapStats,
    )

    assertEquals(
      """
        =================== HEAP SUMMARY ==================
        Class count: 1

        ${sectionHeader("Heap Statistics")}
        Maximum heap size: 2.14GB
        Committed heap size: 1.07GB
        Free heap size: 512MB
        Report reason: OutOfMemory

        ${sectionHeader("Platform Statistics")}
        Projects open: 1
        Project 1:
      """.trimIndent(),
      report,
    )
  }

  @Test
  fun `omits optional sections when statistics are empty`() {
    val report = invokeGetHeapDumpReportText(
      """
        =================== HEAP SUMMARY ==================
        Class count: 1
      """.trimIndent(),
      reason = "None",
      liveStats = "",
      heapStats = "",
    )

    assertEquals(
      """
        =================== HEAP SUMMARY ==================
        Class count: 1
      """.trimIndent(),
      report,
    )
  }

  private fun invokeGetHeapDumpReportText(reportText: String, reason: String, liveStats: String, heapStats: String): String {
    val heapReportProperties = createHeapReportProperties(reason, liveStats, heapStats)
    val method = Class.forName("com.intellij.diagnostic.hprof.action.AnalysisRunnableKt").declaredMethods.single {
      it.name.startsWith("getHeapDumpReportText") && it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
    }
    method.isAccessible = true
    return method.invoke(null, reportText, heapReportProperties) as String
  }

  private fun createHeapReportProperties(reason: String, liveStats: String, heapStats: String): Any {
    val propertiesClass = Class.forName("com.intellij.diagnostic.report.HeapReportProperties")
    val constructor = propertiesClass.declaredConstructors.single { it.parameterCount == 3 }
    constructor.isAccessible = true
    return constructor.newInstance(memoryReportReason(reason), liveStats, heapStats)
  }

  private fun memoryReportReason(name: String): Any {
    return Class.forName("com.intellij.diagnostic.report.MemoryReportReason").enumConstants.single { (it as Enum<*>).name == name }
  }

}
