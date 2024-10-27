// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.impl.local.EelLocalExecApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.tests.eelHelpers.*
import com.intellij.platform.tests.eelHelpers.ttyAndExit.Size
import com.intellij.platform.tests.eelHelpers.ttyAndExit.Command
import com.intellij.platform.tests.eelHelpers.ttyAndExit.GRACEFUL_EXIT_CODE
import com.intellij.platform.tests.eelHelpers.ttyAndExit.HELLO
import com.intellij.platform.tests.eelHelpers.ttyAndExit.INTERRUPT_EXIT_CODE
import com.intellij.platform.tests.eelHelpers.ttyAndExit.TTYState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import io.ktor.util.decodeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class EelLocalExecApiTest {
  companion object {
    private const val PTY_COLS = 42
    private const val PTY_ROWS = 24
    private val NEW_LINES = Regex("\r?\n")

    private lateinit var executor: JavaMainClassExecutor

    @BeforeAll
    @JvmStatic
    fun createExecutor() {
      executor = JavaMainClassExecutor(EelHelper::class.java)
    }
  }


  enum class ExitType() {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }

  enum class PTYManagement {
    NO_PTY, PTY_SIZE_FROM_START, PTY_RESIZE_LATER
  }


  /**
   * Test runs [EelHelper] checking stdin/stdout iteration, exit code, tty and signal/termination handling.
   */
  @CartesianTest
  fun testOutput(
    @CartesianTest.Enum exitType: ExitType,
    @CartesianTest.Enum ptyManagement: PTYManagement,
  ): Unit = timeoutRunBlocking(1.minutes) {

    val builder = executor.createBuilderToExecuteMain()
    builder.pty(when (ptyManagement) {
                  PTYManagement.NO_PTY -> null
                  PTYManagement.PTY_SIZE_FROM_START -> Pty(PTY_COLS, PTY_ROWS, true)
                  PTYManagement.PTY_RESIZE_LATER -> Pty(PTY_COLS - 1, PTY_ROWS - 1, true) // wrong tty size: will resize in the test
                })
    when (val r = localEel.exec.execute(builder)) {
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

        withContext(Dispatchers.Default) {
          val text = ByteBuffer.allocate(1024)
          withTimeoutOrNull(10.seconds) {
            for (chunk in process.stderr) {
              text.put(chunk)
              if (HELLO in chunk.decodeToString()) break
            }
          }
          text.limit(text.position()).rewind()
          assertThat("No ${HELLO} reported in stderr", text.decodeString(), CoreMatchers.containsString(HELLO))
        }


        // Test tty api
        // tty might insert "\r\n", we need to remove them. Hence, NEW_LINES.
        val outputStr = process.stdout.receive().decodeToString().replace(NEW_LINES, "")
        val pyOutputObj = TTYState.deserialize(outputStr)
        when (ptyManagement) {
          PTYManagement.PTY_SIZE_FROM_START, PTYManagement.PTY_RESIZE_LATER -> {
            Assertions.assertNotNull(pyOutputObj.size)
            Assertions.assertEquals(Size(PTY_COLS, PTY_ROWS), pyOutputObj.size, "size must be set for tty")
          }
          PTYManagement.NO_PTY -> {
            Assertions.assertNull(pyOutputObj.size, "size must not be set if no tty")
          }
        }


        // Test kill api
        when (exitType) {
          ExitType.KILL -> process.kill()
          ExitType.TERMINATE -> process.terminate()
          ExitType.INTERRUPT -> {
            // Terminate sleep with interrupt/CTRL+C signal
            process.sendCommand(Command.SLEEP)
            process.interrupt()
          }
          ExitType.EXIT_WITH_COMMAND -> {
            // Just command to ask script return gracefully
            process.sendCommand(Command.EXIT)
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
            when (ptyManagement) {
              PTYManagement.NO_PTY -> Unit // SIGINT is doubtful without PTY especially without console on Windows
              PTYManagement.PTY_SIZE_FROM_START, PTYManagement.PTY_RESIZE_LATER -> {
                // CTRL+C/SIGINT handler returns 42, see script
                assertEquals(INTERRUPT_EXIT_CODE, exitCode)
              }
            }
          }
          ExitType.EXIT_WITH_COMMAND -> {
            assertEquals(GRACEFUL_EXIT_CODE, exitCode) // Graceful exit
          }
        }
      }
    }
  }

  /**
   * Sends [command] to the helper and flush
   */
  private suspend fun EelProcess.sendCommand(command: Command) {
    val text = command.name + "\n"
    stdin.send(text.encodeToByteArray())
  }
}