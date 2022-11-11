// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import org.jetbrains.annotations.ApiStatus;

import java.io.PrintStream;
import java.util.Locale;

/**
 * Remembers the state of some global variables and, from time to time,
 * ensures that they still have the expected values.
 * <p>
 * See IDEA-299881.
 */
@ApiStatus.Internal
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class GlobalState {
  private static final GlobalState ourInstance = new GlobalState();

  private final PrintStream originalSystemOut = System.out;
  private final PrintStream originalSystemErr = System.err;

  /**
   * Check that System.out and System.err still have their initial values.
   */
  public static void checkSystemStreams() {
    ourInstance.checkSystemOut();
    ourInstance.checkSystemErr();
  }

  private void checkSystemOut() {
    PrintStream out = System.out;
    if (out != originalSystemOut) {
      System.setOut(originalSystemOut);
      throwStreamRedirected("System.out", originalSystemOut, out);
    }

    if (originalSystemOut.checkError()) {
      throwStreamClosed("System.out");
    }
  }

  private void checkSystemErr() {
    PrintStream err = System.err;
    if (err != originalSystemErr) {
      System.setErr(originalSystemErr);
      throwStreamRedirected("System.err", originalSystemErr, err);
    }

    if (originalSystemErr.checkError()) {
      throwStreamClosed("System.err");
    }
  }

  /**
   * System.out or System.err has been redirected to another destination, without undoing the redirection afterwards.
   * This prevents all following tests from using this stream for logging.
   * <p>
   * To fix the root cause of this error,
   * set a breakpoint at System.setOut or System.setErr to see where that method is called.
   * Then, make sure that System.out or System.err is properly restored to its previous value,
   * using a classic try-finally block.
   * <p>
   * When this error occurs while setting up a test,
   * the root cause is probably in one of the previous tests,
   * as plain JUnit tests do not detect the redirection by themselves.
   */
  private void throwStreamRedirected(String name, PrintStream original, PrintStream curr) {
    throwMessage("The global '%s' has changed from '%s' to '%s'.", name, original, curr);
  }

  /**
   * System.out or System.err has been closed.
   * This prevents all following tests from using this stream for logging.
   * <p>
   * To fix the root cause of this error,
   * set a breakpoint at PrintStream.close to see where the stream is closed.
   * Then, make sure that the stream is never closed.
   * <p>
   * When this error occurs while setting up a test,
   * the root cause is probably in one of the previous tests,
   * as plain JUnit tests do not detect the closed stream by themselves.
   */
  private void throwStreamClosed(String name) {
    throwMessage("The global '%s' is in error state; maybe it has been closed.", name);
  }

  private void throwMessage(String fmt, Object... args) {
    String msg = GlobalState.class.getName() + ": " + String.format(Locale.ROOT, fmt, args);

    // Warn on all available output channels, hoping that one of them still works.
    originalSystemOut.println(msg);
    originalSystemErr.println(msg);
    throw new IllegalStateException(msg);
  }
}