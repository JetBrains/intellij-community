// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.rt.junit;

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

  class Repeater {
    public static int startRunnerWithArgs(IdeaTestRunner<?> testRunner,
                                          String[] args,
                                          ArrayList<String> listeners,
                                          String name,
                                          int count,
                                          boolean sendTree) {
      testRunner.createListeners(listeners, count);
      if (count == 1) {
        return testRunner.startRunnerWithArgs(args, name, count, sendTree);
      }
      else {
        boolean success = true;
        if (count > 0) {
          int i = 0;
          while (i++ < count) {
            final int result = testRunner.startRunnerWithArgs(args, name, count, sendTree);
            if (result == -2) {
              return result;
            }
            success &= result == 0;
            sendTree = false;
          }

          return success ? 0 : -1;
        }
        else {
          while (true) {
            int result = testRunner.startRunnerWithArgs(args, name, count, sendTree);
            if (result == -2) {
              return -1;
            }
            success &= result == 0;
            if (count == -2 && !success) {
              return -1;
            }
          }
        }
      }
    }
  }
}