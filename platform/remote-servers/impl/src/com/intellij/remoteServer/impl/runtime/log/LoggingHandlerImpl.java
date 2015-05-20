package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.execution.filters.BrowserHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class LoggingHandlerImpl implements LoggingHandler, Disposable {
  private final ConsoleView myConsole;

  public LoggingHandlerImpl(@NotNull Project project) {
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    Disposer.register(this, myConsole);
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
  public void dispose() {

  }
}
