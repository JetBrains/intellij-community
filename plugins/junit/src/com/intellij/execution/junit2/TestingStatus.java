/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.execution.junit2;

import com.intellij.execution.junit2.segments.*;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitListener;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.util.ArrayList;

public class TestingStatus extends ProcessAdapter implements PacketConsumer, InputConsumer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.TestingStatus");

  private JUnitListener myListener;
  private ProcessHandler myProcess;
  private boolean myIsTerminated;
  private DeferedActionsQueue myInvoker;
  private final ArrayList<CompletionEvent> myDeferedEvents = new ArrayList<CompletionEvent>();
  //private Printer myPrinter = Printer.DEAF;
  private InputConsumer myInputConsumer = InputConsumer.DEAF;
  private PacketsDispatcher myPacketsDispatcher;

  public TestingStatus() {
  }

  public TestingStatus(final DeferedActionsQueue invoker) {
    myInvoker = invoker;
  }

  public String getPrefix() {
    return PoolOfDelimiters.TESTS_DONE;
  }

  public void readPacketFrom(final ObjectReader reader) {
    final int time = reader.readInt();
    fireOnRunnerStateChanged(new CompletionEvent(true, time));
  }

  public void onFinished() {
  }

  private void fireOnRunnerStateChanged(final CompletionEvent event) {
    LOG.assertTrue(!myIsTerminated);
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    myPacketsDispatcher.onFinished();
    if (myListener == null)
      myDeferedEvents.add(event);
    else
      myListener.onRunnerStateChanged(event);
    myIsTerminated = !event.isRunning();
  }

  public void setListener(final JUnitListener listener) {
    myListener = listener;
    for (final CompletionEvent event : myDeferedEvents) {
      myListener.onRunnerStateChanged(event);
    }
    myDeferedEvents.clear();
  }

  public boolean isRunning() {
    return !myIsTerminated;
  }

  public void processTerminated(ProcessEvent event) {
    myProcess.removeProcessListener(this);
    myProcess = null;
    IJSwingUtilities.invoke(new Runnable() {
      public void run() {
        myInvoker.addLast(new Runnable() {
          public void run() {
            if (!myIsTerminated) {
              fireOnRunnerStateChanged(new CompletionEvent(false, -1));
            }
          }
        });
      }
    });
  }

  public void attachTo(final ProcessHandler process) {
    myIsTerminated = false;
    myProcess = process;
    myProcess.addProcessListener(this);
  }

  public void onOutput(final String text, final ConsoleViewContentType contentType) {
    myInputConsumer.onOutput(text, contentType);
  }

  public void setInputConsumer(final InputConsumer inputConsumer) {
    myInputConsumer = inputConsumer;
  }

  public void setPacketsDispatcher(final PacketsDispatcher packetsDispatcher) {
    myPacketsDispatcher = packetsDispatcher;
  }
}
