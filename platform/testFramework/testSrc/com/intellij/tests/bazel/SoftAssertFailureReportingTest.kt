// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel

import com.intellij.testFramework.reportSoftAssertFailure
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class SoftAssertFailureReportingTest {
  @ParameterizedTest
  @CsvSource("false,false", "false,true", "true,false", "true,true")
  fun reportsFailure(isTeamCity: Boolean, isBazel: Boolean, @TempDir directory: Path) {
    val classPath = buildList {
      addAll(System.getProperty("java.class.path").split(File.pathSeparator))
      for (loader in generateSequence(javaClass.classLoader) { it.parent }) {
        when (loader) {
          is URLClassLoader -> addAll(loader.urLs.map { Path.of(it.toURI()).toString() })
          else -> {
            val getFiles = loader.javaClass.methods.singleOrNull { it.name == "getFiles" && it.parameterCount == 0 }
            val files = getFiles?.invoke(loader) as? List<*>
            if (files != null) addAll(files.map { it.toString() })
          }
        }
      }
    }.distinct().joinToString(File.pathSeparator)
    val processBuilder = ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-cp", classPath, javaClass.name, directory.toString(),
    )
    val outputs = directory.resolve("outputs")
    processBuilder.environment().apply {
      remove("TEAMCITY_VERSION")
      remove("TEST_UNDECLARED_OUTPUTS_DIR")
      if (isTeamCity) put("TEAMCITY_VERSION", "test")
      if (isBazel) put("TEST_UNDECLARED_OUTPUTS_DIR", outputs.toString())
    }
    val log = directory.resolve("process.log")
    val process = processBuilder.redirectErrorStream(true).redirectOutput(log.toFile()).start()
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "The reporting process did not finish")
      val output = Files.readString(log)
      assertThat(process.exitValue()).withFailMessage(output).isEqualTo(if (isTeamCity) 0 else 1)
      assertThat(output).contains("Leaked snapshot <123>", "Leak details", "stack trace")
      if (isTeamCity && isBazel) {
        assertThat(output).contains("Workspace model leak in test ExampleTest123", "Leak details ]]> & stack trace")
        assertThat(output).doesNotContain("##teamcity[testFailed")
        val reports = Files.list(outputs.resolve("soft-assert-failures")).use { it.toList() }
        assertThat(reports).hasSize(3)
        for (report in reports) {
          assertThat(report.fileName.toString()).endsWith(".xml")
          val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile())
          val testCase = xml.getElementsByTagName("testcase").item(0) as org.w3c.dom.Element
          assertThat(testCase.getAttribute("name"))
            .isEqualTo("(SoftAssertFailure Workspace model leak in test ExampleTest123)")
          val failure = xml.getElementsByTagName("failure").item(0) as org.w3c.dom.Element
          assertThat(failure.getAttribute("message")).isEqualTo("Leaked snapshot <123>")
          assertThat(failure.textContent).contains("Leak details ]]> & stack trace")
        }
        val mainXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(directory.resolve("test.xml").toFile())
        assertThat(mainXml.getElementsByTagName("testcase").length).isZero()
        assertThat(mainXml.getElementsByTagName("failure").length).isZero()
      }
      else {
        assertThat(Files.exists(outputs)).isFalse()
        if (isTeamCity) assertThat(output).contains("##teamcity[testFailed")
      }
    }
    finally {
      process.destroyForcibly()
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val failure = AssertionError("Leaked snapshot <123>", IllegalStateException("Leak details ]]> & stack trace"))
      val plan = LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request().build())
      BazelJUnitOutputListener(Path.of(args[0], "test.xml")).use { listener ->
        listener.testPlanExecutionStarted(plan)
        repeat(2) { reportSoftAssertFailure("Workspace model leak in test ExampleTest123", failure) }
        listener.testPlanExecutionFinished(plan)
        check(!listener.hasTestsWithThrowableResults())
      }
      reportSoftAssertFailure("Workspace model leak in test ExampleTest123", failure)
    }
  }
}
