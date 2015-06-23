/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;

public class ConsoleTerminalHandlerImpl extends TerminalHandlerBase {

  private static final Logger LOG = Logger.getInstance("#" + ConsoleTerminalHandlerImpl.class.getName());

  private final LoggingHandlerImpl myLoggingHandler;

  public ConsoleTerminalHandlerImpl(String presentableName,
                                    Project project,
                                    final InputStream terminalOutput,
                                    final OutputStream terminalInput) {
    super(presentableName);

    myLoggingHandler = new LoggingHandlerImpl(presentableName, project);
    myLoggingHandler.attachToProcess(new ProcessHandler() {
      @Override
      protected void destroyProcessImpl() {

      }

      @Override
      protected void detachProcessImpl() {

      }

      @Override
      public boolean detachIsDefault() {
        return false;
      }

      @Nullable
      @Override
      public OutputStream getProcessInput() {
        return terminalInput;
      }
    });

    Disposer.register(this, myLoggingHandler);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {

      @Override
      public void run() {
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(terminalOutput));
        try {
          while (!isClosed()) {
            String line = outputReader.readLine();
            if (line == null) {
              break;
            }
            myLoggingHandler.print(line + "\n");
          }
        }
        catch (IOException e) {
          LOG.debug(e);
        }
        finally {
          try {
            outputReader.close();
          }
          catch (IOException ignored) {

          }
        }
      }
    });
  }

  @Override
  public JComponent getComponent() {
    return myLoggingHandler.getComponent();
  }
}
