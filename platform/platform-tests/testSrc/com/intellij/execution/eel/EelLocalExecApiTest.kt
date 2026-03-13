// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.eel.EelLocalExecApiTest.PTYManagement.NO_PTY
import com.intellij.execution.eel.EelLocalExecApiTest.PTYManagement.PTY_RESIZE_LATER
import com.intellij.execution.eel.EelLocalExecApiTest.PTYManagement.PTY_SIZE_FROM_START
import com.intellij.execution.eel.processOutputReader.OutStream.STDERR
import com.intellij.execution.eel.processOutputReader.OutStream.STDOUT
import com.intellij.execution.eel.processOutputReader.OutputType
import com.intellij.execution.eel.processOutputReader.ProcessOutputReader
import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.impl.local.getShellFromPasswdRecords
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.eel.where
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.ttyAndExit.Command
import com.intellij.platform.tests.eelHelpers.ttyAndExit.GRACEFUL_EXIT_CODE
import com.intellij.platform.tests.eelHelpers.ttyAndExit.HELLO
import com.intellij.platform.tests.eelHelpers.ttyAndExit.INTERRUPT_EXIT_CODE
import com.intellij.platform.tests.eelHelpers.ttyAndExit.Size
import com.intellij.platform.tests.eelHelpers.ttyAndExit.TTYState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class EelLocalExecApiTest {
  companion object {
    private val logger = fileLogger()
    private const val PTY_COLS = 42
    private const val PTY_ROWS = 24

    private lateinit var executor: JavaMainClassExecutor

    @BeforeAll
    @JvmStatic
    fun createExecutor() {
      executor = JavaMainClassExecutor(EelHelper::class.java, EelHelper.HelperMode.TTY.name)
    }
  }


  enum class ExitType {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }

  enum class PTYManagement(val hasTTY: Boolean) {
    NO_PTY(false), PTY_SIZE_FROM_START(true), PTY_RESIZE_LATER(true)
  }

  @Test
  fun testExitCode(): Unit = timeoutRunBlocking {
    try {
      val r = localEel.exec.spawnProcess("something that doesn't exist for sure").eelIt()
      Assertions.fail("Process shouldn't be created ${r}")
    }
    catch (e: ExecuteProcessException) { // **nix: ENOENT 2 No such file or directory
      // win: ERROR_FILE_NOT_FOUND 2 winerror.h
      Assertions.assertEquals(2, e.errno, "Wrong error code")
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
        timeoutRunBlocking(5.minutes) {
          testOutputImpl(ptyManagement, exitType)
        }
      }
    }
  }

  @ThrowsChecked(ExecuteProcessException::class)
  private suspend fun testOutputImpl(ptyManagement: PTYManagement, exitType: ExitType): Unit = coroutineScope {
    val builder = executor.createBuilderToExecuteMain(localEel.exec)
    builder.interactionOptions(when (ptyManagement) {
                                 NO_PTY -> null
                                 PTY_SIZE_FROM_START -> Pty(PTY_COLS, PTY_ROWS, true)
                                 PTY_RESIZE_LATER -> Pty(PTY_COLS - 1, PTY_ROWS - 1, true) // wrong tty size: will resize in the test
                               })
    val process = builder.eelIt()
    launch {
      try {
        process.exitCode.await()
      }
      catch (e: CancellationException) {
        process.kill()
        throw e
      }
    }
    logger.warn("Process started")

    // Resize tty
    when (ptyManagement) {
      NO_PTY -> {
        try {
          process.resizePty(PTY_COLS, PTY_ROWS)
          Assertions.fail("Exception should have been thrown: process doesn't have pty")
        }
        catch (_: EelProcess.ResizePtyError.NoPty) {
        }
      }
      PTY_SIZE_FROM_START -> Unit
      PTY_RESIZE_LATER -> {
        process.resizePty(PTY_COLS, PTY_ROWS)
        delay(1.seconds) // Resize might take some time
      }
    }

    val outputType =
      if (ptyManagement.hasTTY) {
        OutputType.TTY(PTY_COLS, PTY_ROWS)
      }
      else {
        OutputType.NoTTY(process.stderr)
      }
    ProcessOutputReader(process.stdout, outputType).use { output ->

      withTimeout(30.seconds) {
        while (true) {
          val line = output.get(STDERR)
          if (HELLO in line) {
            break
          }
          else {
            logger.warn("No $HELLO in $line")
            delay(3.seconds)
          }
        }
      }


      var ttyState: TTYState?
      withTimeout(30.seconds) {
        while (true) {
          val fullLine = output.get(STDOUT)
          val line = fullLine.replace(DROP_HELLO, "")
          ttyState = TTYState.deserializeIfValid(line, logger::warn) ?: TTYState.deserializeIfValid(fullLine, logger::warn)
          if (ttyState != null) {
            break
          }
          else {
            logger.warn("No tty in $line (before cut we had $fullLine)")
            delay(3.seconds)
          }
        }
        logger.warn("TTY check finished")
        when (ptyManagement) {
          PTY_SIZE_FROM_START, PTY_RESIZE_LATER -> {
            Assertions.assertNotNull(ttyState.size)
            Assertions.assertEquals(Size(PTY_COLS, PTY_ROWS), ttyState.size, "size must be set for tty")
            if (LocalEelDescriptor.osFamily.isPosix) {
              val expectedTerm = System.getenv("TERM") ?: "xterm"
              Assertions.assertEquals(expectedTerm, ttyState.termName, "Wrong term type")
            }
          }
          NO_PTY -> {
            Assertions.assertNull(ttyState.size, "size must not be set if no tty")
          }
        }
      }

      if (ptyManagement == PTY_RESIZE_LATER && (exitType == ExitType.INTERRUPT || exitType == ExitType.EXIT_WITH_COMMAND) && process.isWinConPtyProcess) {
        delay(10.seconds) // workaround: wait a bit to let ConPTY apply the resize
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
        ExitType.INTERRUPT -> { // Terminate sleep with interrupt/CTRL+C signal
          process.sendCommand(Command.SLEEP)
          process.interrupt()
        }
        ExitType.EXIT_WITH_COMMAND -> { // Just command to ask script return gracefully
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
        if (SystemInfoRt.isWindows) { // We provide 0 as `ExitProcess` on Windows
          assertEquals(0, exitCode)
        }
        else {
          val sigCode = UnixSignal.SIGTERM.getSignalNumber(SystemInfoRt.isMac)
          assertThat("Exit code must be signal code or +128 (if run using shell)",
                     exitCode,
                     anyOf(`is`(sigCode), `is`(sigCode + UnixSignal.EXIT_CODE_OFFSET)))
        }
      }
      ExitType.INTERRUPT -> {
        when (ptyManagement) {
          NO_PTY -> Unit // SIGINT is doubtful without PTY especially without console on Windows
          PTY_SIZE_FROM_START, PTY_RESIZE_LATER -> { // CTRL+C/SIGINT handler returns 42, see script
            assertEquals(INTERRUPT_EXIT_CODE, exitCode)
          }
        }
      }
      ExitType.EXIT_WITH_COMMAND -> {
        assertEquals(GRACEFUL_EXIT_CODE, exitCode) // Graceful exit
      }
    }
  }


  /**
   * `PATH` variable might contain just, it must not break `[where]
   */
  @ParameterizedTest
  @ValueSource(chars = ['\'', ':', ';', ' ', '\b', '\r', '\n', '"', '/', '\\', ' '])
  fun junkInPathDoesNotBreakWhereTest(
    junkChar: Char,
  ): Unit = timeoutRunBlocking(10.minutes) {
    val junkCharStr = junkChar.toString()

    val (shell, _) = localEel.exec.getShell()
    assert(localEel.exec.where(shell.fileName) != null) { "No shell found on PATH: can't check path" }

    // To make sure this mocking work, we look for the shell.
    mockkStatic(PathEnvironmentVariableUtil::class)
    try {
      coEvery { PathEnvironmentVariableUtil.getPathVariableValue() }.returns(junkCharStr)
      assert(localEel.exec.where(shell.fileName) == null) { "Failed to substitute path, real path was used, we test nothing" }

      // These functions shouldn't fail
      localEel.exec.where(junkCharStr)
      localEel.exec.where("file")
    }
    finally {
      unmockkStatic(PathEnvironmentVariableUtil::class)
    }
  }

  @Test
  fun `test getShellFromPasswdRecords`() {
    // docker run --rm ubuntu:24.04 getent passwd
    val records = listOf(
      "# Comments are allowed",
      "root:x:0:0:root:/root:/bin/bash",
      "daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin",
      "bin:x:2:2:bin:/bin:/usr/sbin/nologin",
      "sys:x:3:3:sys:/dev:/usr/sbin/nologin",
      "sync:x:4:65534:sync:/bin:/bin/sync",
      "games:x:5:60:games:/usr/games:/usr/sbin/nologin",
      "man:x:6:12:man:/var/cache/man:/usr/sbin/nologin",
      "lp:x:7:7:lp:/var/spool/lpd:/usr/sbin/nologin",
      "mail:x:8:8:mail:/var/mail:/usr/sbin/nologin",
      "news:x:9:9:news:/var/spool/news:/usr/sbin/nologin",
      "uucp:x:10:10:uucp:/var/spool/uucp:/usr/sbin/nologin",
      "proxy:x:13:13:proxy:/bin:/usr/sbin/nologin",
      "www-data:x:33:33:www-data:/var/www:/usr/sbin/nologin",
      "backup:x:34:34:backup:/var/backups:/usr/sbin/nologin",
      "list:x:38:38:Mailing List Manager:/var/list:/usr/sbin/nologin",
      "irc:x:39:39:ircd:/run/ircd:/usr/sbin/nologin",
      "_apt:x:42:65534::/nonexistent:/usr/sbin/nologin",
      "nobody:x:65534:65534:nobody:/nonexistent:/usr/sbin/nologin",
      "ubuntu:x:1000:1000:Ubuntu:/home/ubuntu:/bin/bash",
      "# commented_ubuntu:x:1001:1001:Ubuntu:/home/ubuntu:/bin/bash",
    )

    getShellFromPasswdRecords(records, 0) shouldBe "/bin/bash"
    getShellFromPasswdRecords(records, 1) shouldBe "/usr/sbin/nologin"
    getShellFromPasswdRecords(records, 1000) shouldBe "/bin/bash"
    getShellFromPasswdRecords(records, 1001) shouldBe null
    getShellFromPasswdRecords(records, 12345) shouldBe null
  }

  /**
   * Sends [command] to the helper and flush
   */
  private suspend fun EelProcess.sendCommand(command: Command) {
    stdin.sendWholeText(command.name + "\r\n") // terminal needs \r\n
  }

  private val EelProcess.isWinConPtyProcess: Boolean
    get() = this is EelWindowsProcess && convertToJavaProcess()::class.java.name == "com.pty4j.windows.conpty.WinConPtyProcess"
}

private val DROP_HELLO = Regex("^.*$HELLO")