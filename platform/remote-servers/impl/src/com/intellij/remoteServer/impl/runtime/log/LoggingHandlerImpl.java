package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.execution.filters.BrowserHyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LoggingHandlerImpl implements LoggingHandler {
  private final ConsoleView myConsole;

  public LoggingHandlerImpl(@NotNull Project project) {
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
  }

  @NotNull
  public ConsoleView getConsole() {
    return myConsole;
  }

  @Override
  public void print(@NotNull String s) {
    myConsole.print(s, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public void printHyperlink(@NotNull String url) {
    myConsole.printHyperlink(url, new BrowserHyperlinkInfo(url));
  }

  public void printlnSystemMessage(@NotNull String s) {
    myConsole.print(s + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler handler) {
    myConsole.attachToProcess(handler);
  }
}
