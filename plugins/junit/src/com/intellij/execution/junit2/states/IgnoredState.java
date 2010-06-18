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

package com.intellij.execution.junit2.states;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;

public class IgnoredState extends ReadableState {
  private TestProxy myPeformedTest;
  private String myIgnoredMessage;
  private String myMessage;
  private String myStackTrace;

  public void initializeFrom(final ObjectReader reader) {
    myPeformedTest = reader.readObject();
    myIgnoredMessage = reader.readLimitedString();
    myMessage = reader.readLimitedString();
    myStackTrace = reader.readLimitedString();
  }

  public void printOn(final Printer printer) {
    String parentName = myPeformedTest.getParent() == null ? myPeformedTest.getInfo().getComment() : myPeformedTest.getParent().toString();
    String message = ExecutionBundle.message("junit.runing.info.ignored.console.message", parentName, myPeformedTest.getInfo().getName());
    printer.print(message + (myIgnoredMessage.length() > 0 ? " (" + myIgnoredMessage + ")": "") + PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    if (myMessage.length() > 0) {
      printer.print(myMessage + PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
    if (myStackTrace.length() > 0) {
      printer.print(myStackTrace + PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }
}
