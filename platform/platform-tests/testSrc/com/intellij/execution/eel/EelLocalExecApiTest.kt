// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.*
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.readAllBytes
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.ttyAndExit.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import io.ktor.util.decodeString
import kotlinx.coroutines.*
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.*
import java.io.IOException
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
      executor = JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.TTY.name)
    }
  }


  enum class ExitType() {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }

  enum class PTYManagement {
    NO_PTY, PTY_SIZE_FROM_START, PTY_RESIZE_LATER
  }

  @Test
  fun testExitCode(): Unit = timeoutRunBlocking {
    when (val r = localEel.exec.execute("something that doesn't exist for sure").eelIt()) {
      is EelResult.Error ->
        // **nix: ENOENT 2 No such file or directory
        // win: ERROR_FILE_NOT_FOUND 2 winerror.h
        Assertions.assertEquals(2, r.error.errno, "Wrong error code")
      is EelResult.Ok -> Assertions.fail("Process shouldn't be created ${r.value}")
    }
  }

  /**
   * Test runs [EelHelper] checking stdin/stdout iteration, exit code, tty and signal/termination handling.
   */
  @TestFactory
  fun testOutput(): List<DynamicTest> {
    val testCases = mutableListOf<Pair<ExitType, PTYManagement>>()
    for (exitType in ExitType.entries) {
      for (ptyManagement in PTYManagement.entries) {
        testCases.add(exitType to ptyManagement)
      }
    }

    testCases.removeIf { (exitType, _) ->
      when (exitType) {
        ExitType.KILL, ExitType.INTERRUPT, ExitType.EXIT_WITH_COMMAND -> false
        ExitType.TERMINATE -> SystemInfoRt.isWindows
      }
    }

    return testCases.map { (exitType, ptyManagement) ->
      DynamicTest.dynamicTest("$exitType $ptyManagement") {
        timeoutRunBlocking(1.minutes) {
          testOutputImpl(ptyManagement, exitType)
        }
      }
    }
  }

  private suspend fun testOutputImpl(ptyManagement: PTYManagement, exitType: ExitType) {
    val builder = executor.createBuilderToExecuteMain(localEel.exec)
    builder.ptyOrStdErrSettings(when (ptyManagement) {
                                  PTYManagement.NO_PTY -> null
                                  PTYManagement.PTY_SIZE_FROM_START -> Pty(PTY_COLS, PTY_ROWS, true)
                                  PTYManagement.PTY_RESIZE_LATER -> Pty(PTY_COLS - 1, PTY_ROWS - 1, true) // wrong tty size: will resize in the test
                                })
    when (val r = builder.eelIt()) {
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

        val text = ByteBuffer.allocate(8192)
        withContext(Dispatchers.Default) {
          withTimeoutOrNull(10.seconds) {
            val helloStream = if (ptyManagement == PTYManagement.NO_PTY) {
              process.stderr
            } else {
              process.stdout // stderr is redirected to stdout when launched with PTY
            }
            while (helloStream.receive(text).getOrThrow() != ReadResult.EOF) {
              if (HELLO in text.slice(0, text.position()).decodeString()) break
            }
          }
          text.limit(text.position()).rewind()
          assertThat("No ${HELLO} reported in stderr", text.decodeString(), CoreMatchers.containsString(HELLO))
        }


        // Test tty api
        var ttyState: TTYState? = null
        text.clear()
        while (ttyState == null) {
          process.stdout.receive(text).getOrThrow()
          // tty might insert "\r\n", we need to remove them, hence, NEW_LINES.
          // Schlemiel the Painter's Algorithm is OK in tests: do not use in production
          ttyState = TTYState.deserializeIfValid(text.slice(0, text.position()).decodeString().replace(NEW_LINES, ""))
        }
        when (ptyManagement) {
          PTYManagement.PTY_SIZE_FROM_START, PTYManagement.PTY_RESIZE_LATER -> {
            Assertions.assertNotNull(ttyState.size)
            Assertions.assertEquals(Size(PTY_COLS, PTY_ROWS), ttyState.size, "size must be set for tty")
            val expectedTerm = System.getenv("TERM") ?: "xterm"
            Assertions.assertEquals(expectedTerm, ttyState.termName, "Wrong term type")
          }
          PTYManagement.NO_PTY -> {
            Assertions.assertNull(ttyState.size, "size must not be set if no tty")
          }
        }

        coroutineScope {
          // TODO Remove this reading after IJPL-186154 is fixed.
          launch {
            process.stdout.readAllBytesAsync(this)
          }
          launch {
            process.stderr.readAllBytesAsync(this)
          }

          // Test kill api
          when (exitType) {
            ExitType.KILL -> process.kill()
            ExitType.TERMINATE -> {
              when (process) {
                is EelPosixProcess -> process.terminate()
                is EelWindowsProcess -> error("No SIGTERM analog for Windows processes")
              }
            }
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
   * Reads all bytes from the channel asynchronously. Otherwise, a PTY process
   * launched with `unixOpenTtyToPreserveOutputAfterTermination=true` won't exit.
   *
   * @see `com.pty4j.PtyProcessBuilder.setUnixOpenTtyToPreserveOutputAfterTermination`
   */
  private fun EelReceiveChannel<IOException>.readAllBytesAsync(coroutineScope: CoroutineScope) {
    coroutineScope.launch {
      readAllBytes()
    }
  }

  /**
   * Sends [command] to the helper and flush
   */
  private suspend fun EelProcess.sendCommand(command: Command) {
    stdin.sendWholeText(command.name + "\n").getOrThrow()
  }
}