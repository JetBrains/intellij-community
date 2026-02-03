// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5.report;

import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestPlan;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

final public class ExecutionState {
  private final PrintStream myOut;

  private TestPlan myPlan;

  private long myCurrentTestStartNanos;
  private int myFinishCount;
  private String myRootName;
  private String myPresentableName;
  private boolean mySuccessful = true;
  private String myIdSuffix = "";
  private boolean mySendTree;

  public ExecutionState(PrintStream out) {
    myOut = out;
  }

  public void setPlan(TestPlan plan) {
    myPlan = plan;
  }

  public TestPlan plan() {
    return myPlan;
  }

  public void print(String line) {
    myOut.println(line);
  }

  public boolean wasSuccessful() {
    return mySuccessful;
  }

  public void updateSuccessful(TestExecutionResult.Status status) {
    mySuccessful &= (status == TestExecutionResult.Status.SUCCESSFUL);
  }

  public void initializeIdSuffix(boolean forked) {
    if (forked && myIdSuffix.isEmpty()) {
      myIdSuffix = String.valueOf(System.currentTimeMillis());
    }
  }

  public void initializeIdSuffix(int i) {
    myIdSuffix = i + "th";
  }

  public String suffix() {
    return myIdSuffix;
  }

  public void setRootName(String rootName) {
    myRootName = rootName;
  }

  public String getRootName() {
    return myRootName;
  }

  public void setPresentableName(String presentableName) {
    myPresentableName = presentableName;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public void setSendTree(boolean sendTree) {
    mySendTree = sendTree;
  }

  public boolean isSendTree() {
    return mySendTree;
  }

  public void onLeafTestStarted() {
    myCurrentTestStartNanos = System.nanoTime();
  }

  public long onLeafTestFinishedAndGetDurationMs() {
    return (System.nanoTime() - myCurrentTestStartNanos) / 1_000_000L;
  }

  public void resetFinishCount() {
    myFinishCount = 0;
  }

  public void incrementFinishCount() {
    myFinishCount++;
  }

  public int finishCount() {
    return myFinishCount;
  }

  public void printRootNameIfNeeded() {
    if (myRootName == null) return;

    int lastPointIdx = myRootName.lastIndexOf('.');
    String name = myRootName;
    String comment = null;
    if (lastPointIdx >= 0) {
      name = myRootName.substring(lastPointIdx + 1);
      comment = myRootName.substring(0, lastPointIdx);
    }

    String messageName = (myPresentableName == null || myPresentableName.isEmpty()) ? name : myPresentableName;

    Map<String, String> attrs = new LinkedHashMap<>();
    attrs.put("name", messageName);
    if (comment != null) attrs.put("comment", comment);
    attrs.put("location", "java:suite://" + myRootName);

    print(MapSerializerUtil.asString(MapSerializerUtil.ROOT_NAME, attrs));
  }
}
