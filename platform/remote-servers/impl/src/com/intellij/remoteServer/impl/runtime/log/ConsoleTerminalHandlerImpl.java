// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ConsoleTerminalHandlerImpl extends TerminalHandlerBase {

  private static final Logger LOG = Logger.getInstance(ConsoleTerminalHandlerImpl.class);

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

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(terminalOutput, StandardCharsets.UTF_8))) {
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
    });
  }

  @Override
  public JComponent getComponent() {
    return myLoggingHandler.getComponent();
  }
}
