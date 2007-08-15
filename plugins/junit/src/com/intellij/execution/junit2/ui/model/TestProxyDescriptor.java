package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

class TestProxyDescriptor extends NodeDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.model.TestProxyDescriptor");
  private static int STATE_UP_TO_DATE = 0;
  private static int STATE_OUT_OF_DATE = 1;
  private static int STATE_UNKNOWN = 2;

  private final TestProxy myTest;
  private int myTimestamp = -1;
  private int myLastChildCount = -1;
  //private int myNeedsUpdate = STATE_UNKNOWN;
  private int myLastMagnitude = -1;

  public TestProxyDescriptor(final Project project, final NodeDescriptor parentDescriptor, final TestProxy test) {
    super(project, parentDescriptor);
    myTest = test;
    myTimestamp = myTest.getStateTimestamp();
    myLastChildCount = myTest.getChildCount();
    myName = test.toString();
  }

  public boolean update() {
    boolean needsUpdate = checkNeedsUpdate();
    myTimestamp = myTest.getStateTimestamp();
    myLastChildCount = myTest.getChildCount();
    return needsUpdate;
  }

  private boolean checkNeedsUpdate() {
    int needsUpdate = STATE_UP_TO_DATE;
    if (myTest.getChildCount() != myLastChildCount) {
      needsUpdate = STATE_OUT_OF_DATE;
    }
    else if (myTest.getStateTimestamp() != myTimestamp) needsUpdate = STATE_UNKNOWN;
    if (needsUpdate == STATE_UNKNOWN) {
      final int magnitude = myTest.getState().getMagnitude();
      needsUpdate = magnitude == myLastMagnitude ? STATE_UP_TO_DATE : STATE_OUT_OF_DATE;
      myLastMagnitude = magnitude;
    }
    if (needsUpdate == STATE_UP_TO_DATE) return false;
    if (needsUpdate == STATE_OUT_OF_DATE) {
      return true;
    }
    LOG.error(String.valueOf(needsUpdate));
    return true;
  }

  public Object getElement() {
    return myTest;
  }
}
