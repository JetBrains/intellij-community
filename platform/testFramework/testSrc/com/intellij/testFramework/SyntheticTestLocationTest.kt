// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")
package com.intellij.testFramework

import com.intellij.openapi.diagnostic.Logger
import com.intellij.tests.testJvmClassPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Checks that a logged error records its synthetic test in the test location artifact,
 * so that the `ResolveTestOwners` build step can attach the code owner to it.
 */
class SyntheticTestLocationTest {
  @ParameterizedTest
  @CsvSource("false,false", "true,false", "true,true")
  fun recordsSyntheticTestLocation(isTeamCity: Boolean, isBazel: Boolean, @TempDir directory: Path) {
    val locations = directory.resolve("test-locations.ndjson")
    val processBuilder = ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-cp", testJvmClassPath(javaClass),
      "-Dintellij.test.location.artifact=$locations",
      "-Didea.log.path=${directory.resolve("log")}",
      javaClass.name,
    )
    processBuilder.environment().apply {
      remove("TEAMCITY_VERSION")
      remove("TEST_UNDECLARED_OUTPUTS_DIR")
      if (isTeamCity) put("TEAMCITY_VERSION", "test")
      if (isBazel) put("TEST_UNDECLARED_OUTPUTS_DIR", directory.resolve("outputs").toString())
    }
    val log = directory.resolve("process.log")
    val process = processBuilder.redirectErrorStream(true).redirectOutput(log.toFile()).start()
    try {
      assertTrue(process.waitFor(60, TimeUnit.SECONDS), "The reporting process did not finish")
      val output = Files.readString(log)
      assertThat(process.exitValue()).withFailMessage(output).isZero()
      assertThat(output)
        .contains("##teamcity[testFailed name='$SYNTHETIC_TEST_NAME'")
        .doesNotContain("Code Owner")
      if (isTeamCity && !isBazel) {
        val lines = Files.readAllLines(locations)
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("""{"test":"$SYNTHETIC_TEST_NAME","class":"${javaClass.name}","failed":true}""")
        assertThat(lines).allSatisfy { line -> assertThat(line.none { it < ' ' }).withFailMessage(line).isTrue() }
        assertThat(lines[1]).contains("\\u001b")
      }
      else {
        assertThat(locations).doesNotExist()
      }
    }
    finally {
      process.destroyForcibly()
    }
  }

  companion object {
    private const val SYNTHETIC_TEST_NAME = "(IdeException Leaked snapshot <NUM>)"

    @JvmStatic
    fun main(args: Array<String>) {
      Logger.setFactory(TestLoggerFactory::class.java)
      TestLoggerFactory.onTestStarted(SyntheticTestLocationTest::class.java)
      recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures {
        Logger.getInstance(SyntheticTestLocationTest::class.java).error("Leaked snapshot 123")
        Logger.getInstance(SyntheticTestLocationTest::class.java).error("Colored \u001b[31mfailure")
      }
    }
  }
}
