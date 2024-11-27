package com.intellij.cce.python.execution.coverage

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import java.io.File
import kotlin.collections.get

class PythonTestCoverageProcessor(
  coverageFilePath: String,
) {
  private var coverageInfo: Map<String, Number> = emptyMap()

  init {
    val jsonString = File(coverageFilePath).readText(Charsets.UTF_8)
    // Parse the JSON string
    val parsedJson = JsonUtil.nextAny(JsonReaderEx(jsonString)) as Map<*, *>
    coverageInfo = parsedJson["totals"] as? Map<String, Number> ?: emptyMap()
  }

  fun getLineCoverage(): Double = coverageInfo["percent_covered"]?.toDouble() ?: 0.0

  fun getBranchCoverage(): Double {
    val totalBranches = coverageInfo["num_branches"]?.toDouble() ?: 0.0
    var branchCoverage = 0.0
    if (totalBranches != 0.0) {
      branchCoverage = (coverageInfo["covered_branches"]?.toDouble() ?: 0.0) / totalBranches
    }

    return branchCoverage
  }
}