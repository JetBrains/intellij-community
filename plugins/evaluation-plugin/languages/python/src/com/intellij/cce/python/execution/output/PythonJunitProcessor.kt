package com.intellij.cce.python.execution.output

import org.w3c.dom.Document
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class PythonJunitProcessor() {
  /**
   * Calculates the success rate of test execution based on the provided junit-xml report.
   * The success rate is determined as the ratio of successfully executed tests
   * to the total number of tests executed.
   *
   * @param junitData The junit-xml report data, containing information about testsuite execution
   *
   * @return The success rate as a Double value in the range [0.0, 1.0]. Returns 0.0 if no tests were executed.
   */
  fun getTestExecutionSuccessRate(junitData: String): Double {
    val inputSource = InputSource(StringReader(junitData))
    val document: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputSource)
    document.documentElement.normalize()

    val attributes = document.getElementsByTagName("testsuite").item(0).attributes
    val totalTests = attributes.getNamedItem("tests").nodeValue.toInt()
    if (totalTests == 0) return 0.0

    val notSuccessfulTests = attributes.getNamedItem("failures").nodeValue.toInt() + attributes.getNamedItem("errors").nodeValue.toInt() + attributes.getNamedItem("skipped").nodeValue.toInt()
    return (totalTests - notSuccessfulTests).toDouble() / totalTests
  }
}