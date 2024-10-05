// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.google.gson.Gson
import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.impl.local.EelLocalExecApi
import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.write
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@TestApplication
class EelLocalExecApiTest {
  companion object {
    const val PYTHON_ENV = "PYTHON"
    private const val PTY_COLS = 42
    private const val PTY_ROWS = 24
    private val NEW_LINES = Regex("\r?\n")

    // TODO: Remove as soon as we migrate to kotlin script from the python
    @BeforeAll
    @JvmStatic
    fun skipTestOnTcWindows() {
      assumeFalse(SystemInfoRt.isWindows && IS_UNDER_TEAMCITY, "Test is disabled on TC@WIN as there is no python by default there")
    }
  }

  private data class TtySize(val cols: Int, val rows: Int)
  private data class PythonOutput(
    val tty: Boolean,
    val size: TtySize?,
  )

  private val helperContent = EelLocalExecApiTest::class.java.classLoader.getResource("helper.py")!!.readBytes()

  // TODO: This tests depends on python interpreter. Rewrite to kotlin script
  private val python = Path.of(System.getenv(PYTHON_ENV)
                               ?: if (!SystemInfoRt.isWindows) "/usr/bin/python3" else error("Provide $PYTHON_ENV env var with path to python"))

  @BeforeEach
  fun setUp() {
    assert(python.isExecutable()) {
      "Can't find python or $python isn't executable. Please set $PYTHON_ENV env var to the path to python binary"
    }
  }


  enum class ExitType() {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }

  enum class PTYManagement {
    NO_PTY, PTY_SIZE_FROM_START, PTY_RESIZE_LATER
  }


  /**
   * Test runs `helper.py` checking stdin/stdout iteration, exit code, tty and signal/termination handling.
   */
  @CartesianTest
  fun testOutput(
    @CartesianTest.Enum exitType: ExitType,
    @CartesianTest.Enum ptyManagement: PTYManagement,
    @TempDir tempDir: Path,
  ): Unit = timeoutRunBlocking {
    val helperScript = tempDir.resolve("helper.py")
    helperScript.write(helperContent)

    val builder = EelExecApi.executeProcessBuilder(python.toString()).args(listOf(helperScript.toString()))
    builder.pty(when (ptyManagement) {
                  PTYManagement.NO_PTY -> null
                  PTYManagement.PTY_SIZE_FROM_START -> Pty(PTY_COLS, PTY_ROWS, true)
                  PTYManagement.PTY_RESIZE_LATER -> Pty(PTY_COLS - 1, PTY_ROWS - 1, true) // wrong tty size: will resize in the test
                })
    when (val r = EelLocalExecApi().execute(builder)) {
      is EelResult.Error -> Assertions.fail(r.error.message)
      is EelResult.Ok -> {
        val process = r.value

        // Resize tty
        when (ptyManagement) {
          PTYManagement.NO_PTY -> {
            try {
              process.resizePty(PTY_COLS, PTY_ROWS)
              Assertions.fail("Exception should have been thrown: process doesn't have pty")
            }
            catch (_: EelProcess.ResizePtyError.NoPty) {
            }
          }
          PTYManagement.PTY_SIZE_FROM_START -> Unit
          PTYManagement.PTY_RESIZE_LATER -> {
            process.resizePty(PTY_COLS, PTY_ROWS)
          }
        }

        Assertions.assertEquals("hello", process.stderr.receive().decodeToString().trim())

        // Test tty api
        // tty might insert "\r\n", we need to remove them. Hence, NEW_LINES.
        val pyOutputStr = process.stdout.receive().decodeToString().replace(NEW_LINES, "")
        val pyOutputObj = Gson().fromJson<PythonOutput>(pyOutputStr, PythonOutput::class.java)
        when (ptyManagement) {
          PTYManagement.PTY_SIZE_FROM_START, PTYManagement.PTY_RESIZE_LATER -> {
            Assertions.assertTrue(pyOutputObj.tty)
            Assertions.assertEquals(TtySize(PTY_COLS, PTY_ROWS), pyOutputObj.size, "size must be set for tty")
          }
          PTYManagement.NO_PTY -> {
            Assertions.assertFalse(pyOutputObj.tty)
            Assertions.assertNull(pyOutputObj.size, "size must not be set if no tty")
          }
        }


        // Test kill api
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