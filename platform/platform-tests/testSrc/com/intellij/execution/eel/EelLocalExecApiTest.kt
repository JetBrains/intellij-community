// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.diagnostic.fileLogger
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
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.*
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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


  enum class ExitType() {
    KILL, TERMINATE, INTERRUPT, EXIT_WITH_COMMAND
  }

  enum class PTYManagement {
    NO_PTY, PTY_SIZE_FROM_START, PTY_RESIZE_LATER
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
                                 PTYManagement.NO_PTY -> null
                                 PTYManagement.PTY_SIZE_FROM_START -> Pty(PTY_COLS, PTY_ROWS, true)
                                 PTYManagement.PTY_RESIZE_LATER -> Pty(PTY_COLS - 1, PTY_ROWS - 1, true) // wrong tty size: will resize in the test
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
        delay(1.seconds) // Resize might take some time
      }
    }
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT) // Not to ignore malformed input
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    val dirtyBuffer = ByteBuffer.allocate(8192)
    val cleanBuffer = CleanBuffer('J')
    withContext(Dispatchers.Default) {
      withTimeoutOrNull(10.seconds) {
        val helloStream = if (ptyManagement == PTYManagement.NO_PTY) {
          process.stderr
        }
        else {
          process.stdout // stderr is redirected to stdout when launched with PTY
        }
        logger.warn("Waiting for $HELLO")
        while (helloStream.receive(dirtyBuffer) != ReadResult.EOF) {
          val line = decoder.decode(dirtyBuffer.flip()).toString()
          logger.warn("Adding raw line '$line'")
          cleanBuffer.add(line)
          dirtyBuffer.clear()
          val fullLine = cleanBuffer.getString()
          if (HELLO in fullLine) {
            break
          }
          else {
            logger.warn("No $HELLO in $fullLine")
          }
        }
      }
      assertThat("No ${HELLO} reported in stderr", cleanBuffer.getString(), CoreMatchers.containsString(HELLO))
    }


    // Test tty api
    logger.warn("Test tty api")
    var ttyState: TTYState?
    cleanBuffer.setPosEnd(HELLO)
    while (true) {

      ttyState = TTYState.deserializeIfValid(cleanBuffer.getString(), logger::warn)
      if (ttyState != null) {
        break
      }
      process.stdout.receive(dirtyBuffer)
      val line = decoder.decode(dirtyBuffer.flip()).toString()
      logger.warn("Line read $line")
      cleanBuffer.add(line)
      dirtyBuffer.clear()
    }
    logger.warn("TTY check finished")
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

    if (ptyManagement == PTYManagement.PTY_RESIZE_LATER && (exitType == ExitType.INTERRUPT || exitType == ExitType.EXIT_WITH_COMMAND) && process.isWinConPtyProcess) {
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
          assertThat("Exit code must be signal code or +128 (if run using shell)", exitCode, anyOf(`is`(sigCode), `is`(sigCode + UnixSignal.EXIT_CODE_OFFSET)))
        }
      }
      ExitType.INTERRUPT -> {
        when (ptyManagement) {
          PTYManagement.NO_PTY -> Unit // SIGINT is doubtful without PTY especially without console on Windows
          PTYManagement.PTY_SIZE_FROM_START, PTYManagement.PTY_RESIZE_LATER -> { // CTRL+C/SIGINT handler returns 42, see script
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

  /**
   * Reads all bytes from the channel asynchronously. Otherwise, a PTY process
   * launched with `unixOpenTtyToPreserveOutputAfterTermination=true` won't exit.
   *
   * @see `com.pty4j.PtyProcessBuilder.setUnixOpenTtyToPreserveOutputAfterTermination`
   */
  private fun EelReceiveChannel.readAllBytesAsync(coroutineScope: CoroutineScope) {
    coroutineScope.launch {
      readAllBytes()
    }
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
