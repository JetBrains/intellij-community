@file:ApiStatus.Internal

package com.intellij.execution.process

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.isLineBreak
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

private const val MAX_CAPTURED_CHARACTERS = 1_000_000

@ApiStatus.Internal
data class CapturedProcessOutputSnapshot(
  val output: String,
  val startLine: Int,
  val endLine: Int,
  val availableStartLine: Int,
  val availableEndLine: Int,
  val isRunning: Boolean,
  val exitCode: Int?,
)

@ApiStatus.Internal
data class CapturedProcessOutputStatus(val isRunning: Boolean, val exitCode: Int?)

@ApiStatus.Internal
class CapturedProcessOutput(
  private val maxCapturedCharacters: Int = MAX_CAPTURED_CHARACTERS,
) {
  private val completeLines = ArrayDeque<String>()
  private val incompleteLine = StringBuilder()
  private var capturedCharacters = 0
  private var availableStartLine = 0
  private var running = true
  private var processExitCode: Int? = null

  val isRunning: Boolean
    @Synchronized get() = running

  val status: CapturedProcessOutputStatus
    @Synchronized get() = CapturedProcessOutputStatus(running, processExitCode)

  @VisibleForTesting
  @Synchronized
  fun append(text: String) {
    for (line in StringUtil.splitByLinesKeepSeparators(text)) {
      if (line.isEmpty()) continue
      val lineCompleted = line.last().isLineBreak()
      if (incompleteLine.isEmpty() && lineCompleted) {
        completeLines.addLast(line)
      }
      else {
        incompleteLine.append(line)
        if (lineCompleted) {
          completeLines.addLast(incompleteLine.toString())
          incompleteLine.clear()
        }
      }
      capturedCharacters += line.length
      trimToCharacterLimit()
    }
  }

  @Synchronized
  fun markStopped(exitCode: Int?) {
    running = false
    if (exitCode != null) {
      processExitCode = exitCode
    }
  }

  @Synchronized
  fun snapshot(startLine: Int?, maxLines: Int): CapturedProcessOutputSnapshot {
    val completeLineCount = completeLines.size
    val availableEndLine = availableStartLine + completeLineCount + if (incompleteLine.isEmpty()) 0 else 1
    val requestedStartLine = startLine ?: availableStartLine
    require(requestedStartLine in availableStartLine..availableEndLine) {
      "startLine $requestedStartLine is outside the available line range $availableStartLine..$availableEndLine."
    }

    val endLine = minOf(requestedStartLine.toLong() + maxLines, availableEndLine.toLong()).toInt()
    val result = buildString {
      for (lineIndex in requestedStartLine until endLine) {
        append(getLine(lineIndex))
      }
    }
    return CapturedProcessOutputSnapshot(
      output = result,
      startLine = requestedStartLine,
      endLine = endLine,
      availableStartLine = availableStartLine,
      availableEndLine = availableEndLine,
      isRunning = running,
      exitCode = processExitCode,
    )
  }

  private fun getLine(absoluteIndex: Int): CharSequence {
    val index = absoluteIndex - availableStartLine
    return if (index < completeLines.size) completeLines[index] else incompleteLine
  }

  private fun trimToCharacterLimit() {
    while (capturedCharacters > maxCapturedCharacters && completeLines.isNotEmpty()) {
      capturedCharacters -= completeLines.removeFirst().length
      availableStartLine++
    }
    if (capturedCharacters > maxCapturedCharacters) {
      capturedCharacters -= incompleteLine.length
      incompleteLine.clear()
      availableStartLine++
    }
  }

  fun attachTo(processHandler: ProcessHandler): Disposable = ProcessOutputCapture(processHandler, this).also { it.start() }
}
