package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.execution.filters.BrowserHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class LoggingHandlerImpl extends LoggingHandlerBase implements LoggingHandler {
  private final ConsoleView myConsole;

  public LoggingHandlerImpl(String presentableName, @NotNull Project project) {
    super(presentableName);
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    Disposer.register(this, myConsole);
  }

  @Override
  public JComponent getComponent() {
    return myConsole.getComponent();
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
    printHyperlink(url, new BrowserHyperlinkInfo(url));
  }

  @Override
  public void printHyperlink(@NotNull String text, HyperlinkInfo info) {
    myConsole.printHyperlink(text, info);
  }

  public void printlnSystemMessage(@NotNull String s) {
    myConsole.print(s + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler handler) {
    myConsole.attachToProcess(handler);
  }

  @Override
  public void clear() {
    myConsole.clear();
  }

  @Override
  public boolean isClosed() {
    return false;
  }
}
