// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SMTRunnerConsoleView extends BaseTestsOutputConsoleView {
  private SMTestRunnerResultsForm myResultsViewer;
  @Nullable private final String mySplitterProperty;
  private final List<AttachToProcessListener> myAttachToProcessListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public SMTRunnerConsoleView(TestConsoleProperties consoleProperties) {
    this(consoleProperties, null);
  }

  /**
   * @param splitterProperty Key to store (project level) latest value of testTree/consoleTab splitter. E.g. "RSpec.Splitter.Proportion"
   */
  public SMTRunnerConsoleView(TestConsoleProperties consoleProperties, @Nullable String splitterProperty) {
    super(consoleProperties, null);

    mySplitterProperty = splitterProperty;
  }

  @Override
  protected TestResultsPanel createTestResultsPanel() {
    // Results View
    myResultsViewer = new SMTestRunnerResultsForm(getConsole().getComponent(),
                                                  getConsole().createConsoleActions(),
                                                  myProperties,
                                                  mySplitterProperty);
    return myResultsViewer;
  }

  @Override
  public void initUI() {
    super.initUI();

    // Console
    myResultsViewer.addEventsListener(new TestResultsViewer.EventsListener() {
      @Override
      public void onSelected(@Nullable SMTestProxy selectedTestProxy,
                             @NotNull TestResultsViewer viewer,
                             @NotNull TestFrameworkRunningModel model) {
        if (selectedTestProxy == null || myResultsViewer.getTreeBuilder().isDisposed()) {
          return;
        }

        // print selected content
        getPrinter().updateOnTestSelected(selectedTestProxy);
      }
    });
  }

  public SMTestRunnerResultsForm getResultsViewer() {
    return myResultsViewer;
  }

  /**
   * Prints a given string of a given type on the root node.
   * Note: it's a permanent printing, as opposed to calling the same method on {@link #getConsole()} instance.
   */
  @Override
  public void print(@NotNull final String s, @NotNull final ConsoleViewContentType contentType) {
    myResultsViewer.getRoot().addLast(new Printable() {
      @Override
      public void printOn(final Printer printer) {
        printer.print(s, contentType);
      }
    });
  }

  /**
   * Prints a given hyperlink on the root node.
   * Note: it's a permanent printing, as opposed to calling the same method on {@link #getConsole()} instance.
   */
  @Override
  public void printHyperlink(@NotNull String hyperlinkText, HyperlinkInfo info) {
    myResultsViewer.getRoot().addLast(new HyperLink(hyperlinkText, info));
  }

  @Override
  public void attachToProcess(@NotNull ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    for (AttachToProcessListener listener : myAttachToProcessListeners) {
      listener.onAttachToProcess(processHandler);
    }
  }

  public void addAttachToProcessListener(@NotNull AttachToProcessListener listener) {
    myAttachToProcessListeners.add(listener);
  }

  @Override
  public void dispose() {
    myAttachToProcessListeners.clear();
    super.dispose();
  }
}
