// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.testFramework.BinaryLightVirtualFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class JUnitReportXmlDetectorTest {
  @Test
  fun `root testsuite`() {
    assertDetected("""<testsuite name="x"></testsuite>""")
  }

  @Test
  fun `root testsuites`() {
    assertDetected("""<testsuites><testsuite name="a"></testsuite></testsuites>""")
  }

  @Test
  fun `prefixed root`() {
    assertDetected("""<ns:testsuites></ns:testsuites>""")
  }

  @Test
  fun `leading comment and xml declaration`() {
    assertDetected(
      """<?xml version="1.0" encoding="UTF-8"?>
        <!-- build metadata -->
        <testsuite name="t"></testsuite>
      """.trimIndent(),
    )
  }

  @Test
  fun `nested comment before root`() {
    assertDetected("<!-- a --><!-- b --><testsuite></testsuite>")
  }

  @Test
  fun `utf8 bom`() {
    assertDetected("\uFEFF<testsuite></testsuite>")
  }

  @Test
  fun `not junit root`() {
    assertNotDetected("""<root><suite name="x"/></root>""")
  }

  @Test
  fun `plain foo root`() {
    assertNotDetected("""<foo></foo>""")
  }

  private fun assertDetected(xml: String) {
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportXml(createVirtualFile(xml)))
  }

  private fun assertNotDetected(xml: String) {
    assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportXml(createVirtualFile(xml)))
  }

  private fun createVirtualFile(xml: String) =
    BinaryLightVirtualFile("report.xml", xml.toByteArray(StandardCharsets.UTF_8))
}
