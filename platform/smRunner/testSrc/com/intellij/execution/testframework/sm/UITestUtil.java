// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Roman Chernyatchik
 */
public class UITestUtil {
  @NotNull
  public static SMTRunnerTestTreeView getTreeOfTests(@NotNull RunContentDescriptor runContentDescriptor) throws InterruptedException {
    SMTRunnerConsoleView consoleView = (SMTRunnerConsoleView)runContentDescriptor.getExecutionConsole();
    SMTRunnerTestTreeView smtRunnerTestTreeView = (SMTRunnerTestTreeView)consoleView.getResultsViewer().getTreeView();
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleView.getResultsViewer().getProperties(), false);

    PlatformTestUtil.expandAll(smtRunnerTestTreeView);

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.waitWhileBusy(smtRunnerTestTreeView);
    return smtRunnerTestTreeView;
  }

  public static class ListOfFragments extends ArrayList<Pair<String, SimpleTextAttributes>> {
    public void add(@NotNull @Nls final String fragment, @NotNull final SimpleTextAttributes attributes) {
      add(new Pair<>(fragment, attributes));
    }
  }

  public static class FragmentsContainer {
    private final UITestUtil.ListOfFragments myFragments;

    public FragmentsContainer() {
      myFragments = new ListOfFragments();
    }

    public void append(@NotNull @Nls final String fragment,
                       @NotNull final SimpleTextAttributes attributes) {
      myFragments.add(fragment, attributes);
    }

    public UITestUtil.ListOfFragments getFragments() {
      return myFragments;
    }

    public String getTextAt(final int index) {
      return myFragments.get(index).first;
    }

    public SimpleTextAttributes getAttribsAt(final int index) {
      return myFragments.get(index).second;
    }
  }
}
