/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.rt.execution.junit;

import java.util.ArrayList;
import java.util.List;

public interface IdeaTestRunner {
  void createListeners(ArrayList listeners, int count);

  /**
   * @return -2 internal failure
   *         -1 there were failed tests
   *          0 all tests were successful
   */
  int startRunnerWithArgs(String[] args, String name, int count, boolean sendTree);

  Object getTestToStart(String[] args, String name);
  List getChildTests(Object description);
  String getStartDescription(Object child);

  String getTestClassName(Object child);

  class Repeater {
    public static int startRunnerWithArgs(IdeaTestRunner testRunner,
                                          String[] args,
                                          ArrayList listeners,
                                          String name,
                                          int count,
                                          boolean sendTree) {
      testRunner.createListeners(listeners, count);
      if (count == 1) {
        return testRunner.startRunnerWithArgs(args, name, count, sendTree);
      }
      else {
        if (count > 0) {
          boolean success = true;
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
          boolean success = true;
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