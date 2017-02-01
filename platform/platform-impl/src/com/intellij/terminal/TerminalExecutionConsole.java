/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.terminal;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.HyperlinkFilter;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.ui.TerminalSession;
import com.jediterm.terminal.util.CharUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class TerminalExecutionConsole implements ExecutionConsole {
  private JBTerminalWidget myTerminalWidget;

  public TerminalExecutionConsole(@NotNull Project project, @NotNull ProcessHandler processHandler) {
    final JBTerminalSystemSettingsProviderBase provider = new JBTerminalSystemSettingsProviderBase();
    AppendableTerminalDataStream dataStream = new AppendableTerminalDataStream();


    myTerminalWidget = new JBTerminalWidget(project, 200, 24, provider, this) {
      @Override
      protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
        return new TerminalStarter(terminal, connector, dataStream);
      }
    };

    TerminalSession session = myTerminalWidget
      .createTerminalSession(new ProcessHandlerTtyConnector(processHandler, EncodingProjectManager.getInstance(project).getDefaultCharset()));

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(ProcessEvent event) {
        session.start();
      }

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        try {
          if (outputType != ProcessOutputTypes.STDOUT) {
            ConsoleViewContentType contentType = ConsoleViewContentType.getConsoleViewType(outputType);
            dataStream.append(encodeColor(contentType.getAttributes().getForegroundColor()));
          }
          dataStream.append(event.getText());
          if (outputType != ProcessOutputTypes.STDOUT) {
            dataStream.append((char)CharUtils.ESC + "[39m"); //restore color
          }

          if (outputType == ProcessOutputTypes.SYSTEM) {
            dataStream.append('\r');
          }
        }
        catch (IOException e) {
          // pass
        }
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        myTerminalWidget.getTerminalPanel().setCursorVisible(false);
      }
    });
  }

  public void addMessageFilter(Project project, Filter filter) {
    myTerminalWidget.addHyperlinkFilter(new HyperlinkFilter() {
      @Override
      public Result apply(String line) {
        Filter.Result r = filter.applyFilter(line, line.length());
        if (r != null) {
          return new Result() {

            @Override
            public List<ResultItem> getResultItems() {
              return r.getResultItems().stream().map((item -> new ResultItem() {
                @Override
                public int getStartOffset() {
                  return item.getHighlightStartOffset();
                }

                @Override
                public int getEndOffset() {
                  return item.getHighlightEndOffset();
                }

                @Override
                public void navigate() {
                  item.getHyperlinkInfo().navigate(project);
                }
              })).collect(Collectors.toList());
            }
          };
        }
        else {
          return null;
        }
      }
    });
  }

  private static String encodeColor(Color color) {
    StringBuilder sb = new StringBuilder();
    sb.append((char)CharUtils.ESC).append("[").append("38;2;").append(color.getRed()).append(";").append(color.getGreen()).append(";")
      .append(color.getBlue()).append("m");
    return sb.toString();
  }

  @Override
  public JComponent getComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public void dispose() {
    myTerminalWidget = null;
  }
}
