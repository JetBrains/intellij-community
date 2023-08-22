// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.TestsRepeater;

import java.util.ArrayList;
import java.util.List;

public interface IdeaTestRunner<T> {
  void createListeners(ArrayList<String> listeners, int count);

  /**
   * @return -2 internal failure
   *         -1 there were failed tests
   *          0 all tests were successful
   */
  int startRunnerWithArgs(String[] args, String name, int count, boolean sendTree);

  T getTestToStart(String[] args, String name);
  List<T> getChildTests(T description);
  String getStartDescription(T child);

  String getTestClassName(T child);

  final class Repeater {
    public static int startRunnerWithArgs(final IdeaTestRunner<?> testRunner,
                                          final String[] args,
                                          ArrayList<String> listeners,
                                          final String name,
                                          final int count,
                                          boolean sendTree) {
      testRunner.createListeners(listeners, count);
      try {
        return TestsRepeater.repeat(count, sendTree, new TestsRepeater.TestRun() {
          @Override
          public int execute(boolean sendTree) {
            return testRunner.startRunnerWithArgs(args, name, count, sendTree);
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace(System.err);
        return -2;
      }
    }
  }
}