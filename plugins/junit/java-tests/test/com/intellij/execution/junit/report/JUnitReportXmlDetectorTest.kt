// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

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
    val inner = "<testsuite></testsuite>".toByteArray(StandardCharsets.UTF_8)
    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val bytes = bom + inner
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportXml(bytes, bytes.size))
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
    val bytes = xml.toByteArray(StandardCharsets.UTF_8)
    assertTrue(JUnitReportXmlDetector.looksLikeJUnitReportXml(bytes, bytes.size))
  }

  private fun assertNotDetected(xml: String) {
    val bytes = xml.toByteArray(StandardCharsets.UTF_8)
    assertFalse(JUnitReportXmlDetector.looksLikeJUnitReportXml(bytes, bytes.size))
  }
}
