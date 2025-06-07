// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.ttyAndExit

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


/**
 * 1. prints hello into stderr
 * 2. prints tty and its size to stdout
 * 3. waits for command exit (exit 0) or sleep (sleep 15_000)
 * 4. installs signal for SIGINT to return 42
 */
@TestOnly
internal fun startTtyAndExitHelper() {

  // Exit once SIGINT sent without terminal
  Signal.handle(Signal("INT"), OnSigint)

  val terminal = TerminalBuilder.terminal()
  val terminalSize = if (terminal.type == Terminal.TYPE_DUMB) null else terminal.size

  // Exit once SIGINT sent with terminal
  terminal.handle(Terminal.Signal.INT, OnSigint)

  // The first thing to do is to print this to the stderr
  // print as chunks to emulate a slow process
  for (part in HELLO.chunked(2)) {
    System.err.print(part)
    System.err.flush()
    Thread.sleep(10)
  }
  System.err.print('\n')
  System.err.flush()

  val ttyState = TTYState(terminalSize?.let { Size(cols = it.columns, rows = it.rows) }, termName = System.getenv("TERM"))

  // Then, print terminal info to the stdout, again in chunks as in a slow process
  for (chunk in ttyState.serialize().chunked(2)) {
    print(chunk)
    System.out.flush()
  }
  println()
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