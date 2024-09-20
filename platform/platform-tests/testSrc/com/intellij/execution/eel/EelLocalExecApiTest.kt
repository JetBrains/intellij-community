// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.impl.local.EelLocalExecApi
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@TestApplication
class EelLocalExecApiTest {
  companion object {
    const val PYTHON_ENV = "PYTHON"
  }

  private val helperContent = EelLocalExecApiTest::class.java.classLoader.getResource("helper.py")!!.readBytes()

  // TODO: This tests depends on python interpreter. Rewrite to something linked statically
  private val python = Path.of(System.getenv(PYTHON_ENV) ?: "/usr/bin/python3")

  @BeforeEach
  fun setUp() {
    assert(python.isExecutable()) {
      "Can't find python or $python isn't executable. Please set $PYTHON_ENV env var to the path to python binary"
    }
  }


  enum class ExitType() {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }


  /**
   * Test runs `helper.py` checking stdin/stdout iteration, exit code and signal/termination handling.
   */
  @ParameterizedTest
  @EnumSource(ExitType::class)
  fun testOutput(exitType: ExitType, @TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val helperScript = tempDir.resolve("helper.py")
    helperScript.write(helperContent)

    val builder = EelExecApi.executeProcessBuilder(python.toString()).args(listOf(helperScript.toString()))
    when (val r = EelLocalExecApi().execute(builder)) {
      is EelExecApi.ExecuteProcessResult.Failure -> Assertions.fail(r.message)
      is EelExecApi.ExecuteProcessResult.Success -> {
        val process = r.process
        val welcome = process.stdout.receive().decodeToString()
        // Script starts with tty:False/True, size:[tty size if any]
        assertThat("Welcome string is wrong", welcome, allOf(containsString("tty"), containsString("size")))
        println(welcome)
        when (exitType) {
          ExitType.KILL -> process.kill()
          ExitType.TERMINATE -> process.terminate()
          ExitType.INTERRUPT -> {
            // Terminate sleep with interrupt/CTRL+C signal
            process.stdin.send("sleep\n".encodeToByteArray())
            assertEquals("sleeping", process.stdout.receive().decodeToString().trim())
            process.interrupt()
          }
          ExitType.EXIT_WITH_COMMAND -> {
            // Just command to ask script return gracefully
            process.stdin.send("exit\n".encodeToByteArray())
          }
        }
        val exitCode = process.exitCode.await()
        when (exitType) {
          ExitType.KILL -> {
            assertNotEquals(0, exitCode) //Brutal kill is never 0
          }
          ExitType.TERMINATE -> {
            if (SystemInfoRt.isWindows) {
              // We provide 0 as `ExitProcess` on Windows
              assertEquals(0, exitCode)
            }
            else {
              val sigCode = UnixSignal.SIGTERM.getSignalNumber(SystemInfoRt.isMac)
              assertThat("Exit code must be signal code or +128 (if run using shell)",
                         exitCode, anyOf(`is`(sigCode), `is`(sigCode + UnixSignal.EXIT_CODE_OFFSET)))
            }
          }
          ExitType.INTERRUPT -> {
            assertEquals(42, exitCode) // CTRL+C/SIGINT handler returns 42, see script
          }
          ExitType.EXIT_WITH_COMMAND -> {
            assertEquals(0, exitCode) // Graceful exit
          }
        }
      }
    }
  }
}