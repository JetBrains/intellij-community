/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;

public class MockPrinter implements Printer {
  private boolean myShouldReset = false;
  private boolean myHasPrinted = false;
  protected final StringBuilder myAllOut = new StringBuilder();
  protected final StringBuilder myStdOut = new StringBuilder();
  protected final StringBuilder myStdErr = new StringBuilder();
  protected final StringBuilder myStdSys = new StringBuilder();

  /**
   * Creates printer and prints printable on it.
   * @param printable printable to print on this printer
   * @return printer filled with printable output
   */
  @NotNull
  public static MockPrinter fillPrinter(@NotNull Printable printable) {
    MockPrinter printer = new MockPrinter();
    printable.printOn(printer);
    return printer;
  }

  public MockPrinter() {
    this(true);
  }

  public MockPrinter(boolean shouldReset) {
    myShouldReset = shouldReset;
  }

  @Override
  public void print(String s, ConsoleViewContentType contentType) {
    myHasPrinted = true;
    myAllOut.append(s);
    if (contentType == ConsoleViewContentType.NORMAL_OUTPUT) {
      myStdOut.append(s);
    }
    else if (contentType == ConsoleViewContentType.ERROR_OUTPUT) {
      myStdErr.append(s);
    }
    else if (contentType == ConsoleViewContentType.SYSTEM_OUTPUT) {
      myStdSys.append(s);
    }
  }

  public String getAllOut() {
    return myAllOut.toString();
  }

  public String getStdOut() {
    return myStdOut.toString();
  }

  public String getStdErr() {
    return myStdErr.toString();
  }

  public String getStdSys() {
    return myStdSys.toString();
  }

  public void setHasPrinted(final boolean hasPrinted) {
    myHasPrinted = hasPrinted;
  }

  public boolean isShouldReset() {
    return myShouldReset;
  }

  public void resetIfNecessary() {
    if (isShouldReset()) {
      myStdErr.setLength(0);
      myStdOut.setLength(0);
      myStdSys.setLength(0);
    }
    setHasPrinted(false);
  }

  public boolean hasPrinted() {
    return myHasPrinted;
  }

  @Override
  public void onNewAvailable(@NotNull Printable printable) {
    printable.printOn(this);
  }

  @Override
  public void printHyperlink(String text, HyperlinkInfo info) {
  }

  @Override
  public void mark() {}
}
