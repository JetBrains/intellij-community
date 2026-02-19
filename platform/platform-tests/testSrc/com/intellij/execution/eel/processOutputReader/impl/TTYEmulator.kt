// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel.processOutputReader.impl

import com.intellij.terminal.AppendableTerminalDataStream
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.TerminalDataStream
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * XTERM emulator. Provide each line to [putLine] (or [putChar]).
 * Eventually your output, free from all ESC commands, will become available via [getLines].
 * When finished, call [close]
 *
 * *Warning*: Forgetting to call [close] might freeze [scope] forever: it uses blocking (non suspend) calls.
 */
internal class TTYEmulator(scope: CoroutineScope, width: Int, height: Int) : AutoCloseable {
  private val buf = TerminalTextBuffer(width, height, StyleState())
  private val terminal = JediTerminal(MyTerminalDisplay(), buf, StyleState())
  private val dataStream = MyStream()
  private val emulator = JediEmulator(dataStream, terminal)

  init {
    scope.launch(Dispatchers.IO) {
      while (emulator.hasNext()) {
        emulator.next()
        ensureActive()
      }
    }
  }

  fun putChar(char: Char) {
    dataStream.append(char)
  }

  fun getLines(): String = buf.getScreenLines()

  override fun close() {
    dataStream.stop = true
    dataStream.append(0.toChar())
  }
}

internal fun TTYEmulator.putLine(line: String) {
  for (c in line) {
    putChar(c)
  }
}


private class MyTerminalDisplay : TerminalDisplay {
  override fun setCursor(x: Int, y: Int) = Unit
  override fun setCursorShape(cursorShape: CursorShape?) = Unit
  override fun beep() = Unit
  override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) = Unit
  override fun setCursorVisible(isCursorVisible: Boolean) = Unit
  override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) = Unit
  override fun getWindowTitle(): String = ""
  override fun setWindowTitle(windowTitle: String) = Unit
  override fun getSelection(): TerminalSelection? = null
  override fun terminalMouseModeSet(mouseMode: MouseMode) = Unit
  override fun setMouseFormat(mouseFormat: MouseFormat) = Unit
  override fun ambiguousCharsAreDoubleWidth(): Boolean = true
}

private class MyStream(private val s: AppendableTerminalDataStream = AppendableTerminalDataStream()) : TerminalDataStream by s, Appendable by s {
  @Volatile
  var stop: Boolean = false
  override fun getChar(): Char {
    if (stop) {
      throw TerminalDataStream.EOF()
    }
    return s.char
  }
}