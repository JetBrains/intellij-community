/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;

public interface IdeaTestRunner {
  int startRunnerWithArgs(String[] args);

  void setStreams(SegmentedOutputStream segmentedOut, SegmentedOutputStream segmentedErr);
}