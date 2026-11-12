// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")
package com.intellij.tests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Checks that [TestLocationStorage] writes each test name as a valid JSON string, also when the name holds a control character.
 */
class TestLocationStorageTest {
  @Test
  fun escapesTestNames(@TempDir directory: Path) {
    val locations = directory.resolve("test-locations.ndjson")
    val log = directory.resolve("process.log")
    val process = ProcessBuilder(
      Path.of(System.getProperty("java.home"), "bin", "java").toString(),
      "-cp", testJvmClassPath(javaClass),
      "-Dintellij.test.location.artifact=$locations",
      javaClass.name,
    ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
    try {
      assertTrue(process.waitFor(60, TimeUnit.SECONDS), "The recording process did not finish")
      assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero()
      assertThat(Files.readAllLines(locations)).containsExactly(
        """{"test":"quote \" backslash \\ tab \t newline \n","class":"${javaClass.name}","failed":true}""",
        """{"test":"escape \u001b[31m bell \u0007 null \u0000","class":"${javaClass.name}","failed":false}""",
      )
    }
    finally {
      process.destroyForcibly()
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      TestLocationStorage.recordTestLocation("quote \" backslash \\ tab \t newline \n", TestLocationStorageTest::class.java, true)
      TestLocationStorage.recordTestLocation("escape \u001b[31m bell \u0007 null \u0000", TestLocationStorageTest::class.java, false)
    }
  }
}
