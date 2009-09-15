/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;

import java.util.ArrayList;

public interface IdeaTestRunner {
  int startRunnerWithArgs(String[] args, ArrayList listeners);

  void setStreams(SegmentedOutputStream segmentedOut, SegmentedOutputStream segmentedErr);
}