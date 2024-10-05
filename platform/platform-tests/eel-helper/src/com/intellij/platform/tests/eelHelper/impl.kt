// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelper

import org.jetbrains.annotations.TestOnly
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import sun.misc.Signal
import sun.misc.SignalHandler
import kotlin.system.exitProcess

@TestOnly
private object OnSigint : SignalHandler, Terminal.SignalHandler {
  override fun handle(sig: Signal?) {
    onSigInt()
  }

  override fun handle(signal: Terminal.Signal?) {
    onSigInt()
  }

  private fun onSigInt() {
    exitProcess(INTERRUPT_EXIT_CODE)
  }
}


@TestOnly
internal fun startHelper() {

  // Exit once SIGINT sent without terminal
  Signal.handle(Signal("INT"), OnSigint)

  val terminal = TerminalBuilder.terminal()
  val terminalSize = if (terminal.type == Terminal.TYPE_DUMB) null else terminal.size

  // Exit once SIGINT sent with terminal
  terminal.handle(Terminal.Signal.INT, OnSigint)

  // First thing to do is to print this to the stderr
  System.err.print(HELLO + "\n")
  System.err.flush()

  val ttyState = TTYState(terminalSize?.let{ Size(cols = it.columns, rows = it.rows )})

  // Then, print terminal info to the stdout
  println(ttyState.serialize())
  System.out.flush()

  when (Command.valueOf(readln().trim().uppercase())) {
    Command.EXIT -> {
      exitProcess(GRACEFUL_EXIT_CODE)
    }
    Command.SLEEP -> {
      Thread.sleep(15_000)
    }
  }
}