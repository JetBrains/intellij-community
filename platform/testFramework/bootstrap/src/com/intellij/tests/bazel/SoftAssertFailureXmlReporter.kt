// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel

import org.junit.platform.engine.TestExecutionResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import javax.xml.stream.XMLOutputFactory

object SoftAssertFailureXmlReporter {
  fun writeReport(outputDir: Path, testName: String, failure: AssertionError): Path {
    val reportsDir = Files.createDirectories(outputDir.resolve("soft-assert-failures"))
    val temporaryFile = Files.createTempFile(reportsDir, "failure-", ".tmp")
    try {
      Files.newBufferedWriter(temporaryFile).use { writer ->
        val xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(writer)
        try {
          xml.writeStartDocument("UTF-8", "1.0")
          xml.writeStartElement("testsuite")
          xml.writeAttribute("name", "Synthetic tests")
          xml.writeAttribute("tests", "1")
          xml.writeAttribute("failures", "1")
          xml.writeStartElement("testcase")
          xml.writeAttribute("name", SafeXml.escapeIllegalCharacters(testName))
          xml.writeAttribute("classname", "")
          xml.writeAttribute("time", "0")
          xml.writeStartElement("failure")
          TestCaseXmlRenderer.writeThrowableMessage(xml, TestExecutionResult.failed(failure))
          xml.writeEndElement()
          xml.writeEndElement()
          xml.writeEndElement()
          xml.writeEndDocument()
        }
        finally {
          xml.close()
        }
      }
      val report = temporaryFile.resolveSibling(temporaryFile.fileName.toString().removeSuffix(".tmp") + ".xml")
      return Files.move(temporaryFile, report, ATOMIC_MOVE)
    }
    finally {
      Files.deleteIfExists(temporaryFile)
    }
  }
}
