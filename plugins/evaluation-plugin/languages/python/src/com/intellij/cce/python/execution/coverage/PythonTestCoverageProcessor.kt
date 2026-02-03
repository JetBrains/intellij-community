package com.intellij.cce.python.execution.coverage

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil

class PythonTestCoverageProcessor(
  coverageData: String,
  targetFile: String,
) {
  private val totalCoverageInfo: Map<String, Number>
  private val functionsCoverageInfo: Map<String, Any>

  init {
    val parsedJson = JsonUtil.nextAny(JsonReaderEx(coverageData)) as Map<*, *>
    functionsCoverageInfo = ((parsedJson["files"] as Map<*, *>)[targetFile] as Map<*, *>)["functions"] as Map<String, Number>
    totalCoverageInfo = parsedJson["totals"] as? Map<String, Number> ?: emptyMap()
  }

  fun getLineCoverage(): Double = (totalCoverageInfo["percent_covered"]?.toDouble() ?: 0.0) / 100

  fun getLineCoverage(unitUnderTest: PsiNamedElement?): Double {
    val functionName = getFunctionName(unitUnderTest)
    functionName ?: return getLineCoverage()
    val summary = (functionsCoverageInfo[functionName] as Map<*, *>)["summary"] as Map<String, Number>
    val coveredLines = summary["covered_lines"]
    val missingLines = summary["missing_lines"]
    val totalLines = coveredLines?.toInt()?.plus(missingLines?.toInt() ?: 0) ?: 0

    if (totalLines == 0) return 1.0
    return (coveredLines?.toDouble() ?: 0.0) / totalLines.toDouble()
  }

  fun getBranchCoverage(): Double {
    val totalBranches = totalCoverageInfo["num_branches"]?.toDouble() ?: 0.0
    var branchCoverage = 0.0
    if (totalBranches != 0.0) {
      branchCoverage = (totalCoverageInfo["covered_branches"]?.toDouble() ?: 0.0) / totalBranches
    }

    return branchCoverage
  }

  fun getBranchCoverage(unitUnderTest: PsiNamedElement?): Double {
    val functionName = getFunctionName(unitUnderTest)
    functionName ?: return getBranchCoverage()
    val summary = (functionsCoverageInfo[functionName] as Map<*, *>)["summary"] as Map<String, Number>
    val coveredBranches = summary["covered_branches"]
    val missingBranches = summary["missing_branches"]
    val totalBranches = coveredBranches?.toInt()?.plus(missingBranches?.toInt() ?: 0) ?: 0

    if (totalBranches == 0) return 0.0
    return (coveredBranches?.toDouble() ?: 0.0) / totalBranches.toDouble()
  }

  private fun getFunctionName(unitUnderTest: PsiNamedElement?) = unitUnderTest?.let {
    val clazz = PsiTreeUtil.getParentOfType(unitUnderTest, PyClass::class.java)
    val functionName = unitUnderTest.name
    clazz?.let { "${it.name}.$functionName" } ?: functionName
  }
}