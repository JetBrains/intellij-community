// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TestResultsViewer extends Disposable {
  /**
   * Fake Root for toplevel test suits/tests
   *
   * @return root
   */
  SMTestProxy getTestsRootNode();

  /**
   * Selects test or suite in Tests tree and notify about selection changed
   *
   * @param proxy
   */
  void selectAndNotify(@Nullable AbstractTestProxy proxy);

  void addEventsListener(EventsListener listener);

  interface EventsListener extends TestProxyTreeSelectionListener {
    default void onTestingStarted(@NotNull TestResultsViewer sender) {
    }

    default void onTestingFinished(@NotNull TestResultsViewer sender) {
    }

    default void onTestNodeAdded(@NotNull TestResultsViewer sender, @NotNull SMTestProxy test) {
    }

    @Override
    default void onSelected(@Nullable SMTestProxy selectedTestProxy,
                            @NotNull TestResultsViewer viewer,
                            @NotNull TestFrameworkRunningModel model) {
    }
  }

  /**
   * @deprecated Use {@link EventsListener} directly.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  class SMEventsAdapter implements EventsListener {
  }
}
