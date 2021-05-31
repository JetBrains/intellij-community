// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process

class PtyCommandLineOptions private constructor(val consoleMode: Boolean,
                                                val useCygwinLaunch: Boolean,
                                                val initialColumns: Int,
                                                val initialRows: Int) {

  override fun toString(): String {
    return "consoleMode=$consoleMode, useCygwinLaunch=$useCygwinLaunch, initialColumns=$initialColumns, initialRows=$initialRows"
  }

  fun builder(): Builder {
    return Builder(consoleMode, useCygwinLaunch, initialColumns, initialRows)
  }

  companion object {
    @JvmField
    val DEFAULT = PtyCommandLineOptions(false, false, -1, -1)
  }

  class Builder internal constructor(private var consoleMode: Boolean,
                                     private var useCygwinLaunch: Boolean,
                                     private var initialColumns: Int,
                                     private var initialRows: Int) {

    /**
     * @param consoleMode `true` means that started process output will be shown using `ConsoleViewImpl`:
     * 1. TTY echo is disabled (like `stty -echo`) to use manual input text buffer provided by `ConsoleViewImpl`.
     * 2. Separate process `stderr` will be available. For example, `stderr` and `stdout` are merged in a terminal.
     *
     * `false` means that started process output will be shown using `TerminalExecutionConsole` that is based on a terminal emulator.
     */
    fun consoleMode(consoleMode: Boolean) = apply { this.consoleMode = consoleMode }
    fun consoleMode() = consoleMode
    fun useCygwinLaunch(useCygwinLaunch: Boolean) = apply { this.useCygwinLaunch = useCygwinLaunch }
    fun useCygwinLaunch() = useCygwinLaunch
    fun initialColumns(initialColumns: Int) = apply { this.initialColumns = initialColumns }
    fun initialColumns() = initialColumns
    fun initialRows(initialRows: Int) = apply { this.initialRows = initialRows }
    fun initialRows() = initialRows

    fun build() = PtyCommandLineOptions(consoleMode, useCygwinLaunch, initialColumns, initialRows)

    fun set(options: PtyCommandLineOptions) = apply {
      consoleMode = options.consoleMode
      useCygwinLaunch = options.useCygwinLaunch
      initialColumns = options.initialColumns
      initialRows = options.initialRows
    }

    override fun toString(): String {
      return "consoleMode=$consoleMode, useCygwinLaunch=$useCygwinLaunch, initialColumns=$initialColumns, initialRows=$initialRows"
    }
  }
}