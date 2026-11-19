// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.rt.junit;

import com.intellij.rt.execution.TestListenerProtocol;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import com.intellij.rt.execution.junit.TestsRepeater;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface IdeaTestRunner<T> {
  void createListeners(ArrayList<String> listeners, int count);

  /**
   * @param parentId the id of the tree node that gets the nodes of this run. It is null when the runner reports no node ids.
   * @return -2 internal failure
   *         -1 there were failed tests
   *          0 all tests were successful
   */
  int startRunnerWithArgs(String[] args, String name, int count, String parentId, boolean sendTree);

  /**
   * @return true when the runner reports each node with an id and with the id of its parent
   */
  default boolean isIdBasedTestTree() {
    return false;
  }

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
        if (testRunner.isIdBasedTestTree()) {
          return repeatUnderSuites(testRunner, args, name, count, sendTree);
        }
        return TestsRepeater.repeat(count, sendTree, new TestsRepeater.TestRun() {
          @Override
          public int execute(boolean sendTree) {
            return testRunner.startRunnerWithArgs(args, name, count, null, sendTree);
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace(System.err);
        return -2;
      }
    }

    /**
     * Repeats the run as {@link TestsRepeater} does, and reports a suite for each repetition. The run gets the id of the suite as
     * the parent. This loop is here and not in {@link TestsRepeater}: the classes of this module come from the installed IDE,
     * and {@link TestsRepeater} comes from the class path of the project, so the two can have different versions.
     */
    private static int repeatUnderSuites(IdeaTestRunner<?> testRunner, String[] args, String name, int count, boolean sendTree) {
      if (count == 1) {
        return testRunner.startRunnerWithArgs(args, name, count, TestListenerProtocol.ROOT_NODE_ID, sendTree);
      }
      PrintStream out = System.out;
      // A forked process sends no tree. Its sibling processes report the same repetitions, so the id of a repetition needs the process.
      String processId = sendTree ? "" : String.valueOf(System.currentTimeMillis());
      boolean success = true;
      for (int repetition = 1; count < 0 || repetition <= count; repetition++) {
        String suiteName = "[" + repetition + "]";
        String suiteId = suiteName + processId;
        Map<String, String> suite = new LinkedHashMap<>();
        suite.put("id", suiteId);
        suite.put("name", suiteName);
        suite.put("nodeId", suiteId);
        suite.put("parentNodeId", TestListenerProtocol.ROOT_NODE_ID);
        Map<String, String> startedSuite = new LinkedHashMap<>(suite);
        // The suite of a repetition is no node of the test framework.
        startedSuite.put("origId", "");
        out.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_SUITE_STARTED, startedSuite));
        int result;
        try {
          result = testRunner.startRunnerWithArgs(args, name, count, suiteId, sendTree);
        }
        finally {
          out.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_SUITE_FINISHED, suite));
        }
        if (result == -2) {
          return count > 0 ? -2 : -1;
        }
        success &= result == 0;
        if (count == -2 && !success) {
          return -1;
        }
        if (count == -3 && result == 0) {
          return 0;
        }
      }
      return success ? 0 : -1;
    }
  }
}