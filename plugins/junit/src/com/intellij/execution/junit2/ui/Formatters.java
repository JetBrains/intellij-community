/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.TestInfo;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.ExecutionBundle;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import java.text.NumberFormat;

public class Formatters {
  public static String printTest(final TestProxy test) {
    if (test == null || test.getInfo() == null)
      return "";
    final TestInfo info = test.getInfo();
    return info.getName() + sensibleCommentFor(test);
  }

  public static String printMemory(final long memory) {
    final String string = printMemoryUnsigned(memory);
    return memory > 0 ? "+" + string : string;
  }

  public static String printMemoryUnsigned(final long memory) {
    return ExecutionBundle.message("junit.runing.info.memory.available.kb.message", NumberFormat.getInstance().format((double)memory/1024.0));
  }

  public static TestStatistics statisticsFor(final TestProxy test) {
    if (test == null)
      return TestStatistics.ABCENT;
    final TestState state = test.getState();
    final int magnitude = state.getMagnitude();
    if (magnitude == PoolOfTestStates.NOT_RUN_INDEX)
      return TestStatistics.NOT_RUN;
    if (test.isLeaf())
      return state.isFinal() ?
          actualInfo(test) :
          TestStatistics.RUNNING;
    final ActualStatistics actualStatistics = actualInfo(test);
    if (state.isInProgress())
      actualStatistics.setRunning();
    return actualStatistics;
  }

  private static ActualStatistics actualInfo(final TestProxy test) {
    return new ActualStatistics(test.getStatistics());
  }

  private static boolean isCommentSensible(final TestProxy testProxy) {
    final TestInfo info = testProxy.getInfo();
    if (info.getComment().length() == 0)
      return false;
    final TestProxy parent = testProxy.getParent();
    return parent == null || !fullLabelOf(parent.getInfo()).equals(info.getComment());
  }

  private static String fullLabelOf(final TestInfo info) {
    return info.getComment() + "." +  info.getName();
  }

  public static String sensibleCommentFor(final TestProxy testProxy) {
    final String sensibleComment;
    if (isCommentSensible(testProxy)) {
      sensibleComment = " (" + testProxy.getInfo().getComment() + ")";
    }
    else {
      sensibleComment = "";
    }
    return sensibleComment;
  }

  public static String printMemoryMega(final long memory) {
    final double unit = 1024*1024;
    return ExecutionBundle.message("junit.runing.info.memory.available.mb.message", NumberFormat.getInstance().format((double)memory/unit));
  }

  public static String printFullKBMemory(final long memory) {
    return  ExecutionBundle.message("memory.available.message", NumberFormat.getInstance().format(memory / 1024));
  }

  public static String printMinSec(final long millis) {
    final long seconds = millis / 1000;
    if (seconds == 0) return "";
    final long min = seconds / 60;
    final long sec = seconds % 60;
    return min + ":" + formatInt(sec, 2);
  }

  public static String formatInt(final long value, final int minDigits) {
    String strValue = Long.toString(value);
    for (int i = strValue.length(); i < minDigits; i++) strValue = "0" + strValue;
    return strValue;
  }
}
