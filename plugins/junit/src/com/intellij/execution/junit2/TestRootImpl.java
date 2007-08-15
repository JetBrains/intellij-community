package com.intellij.execution.junit2;

import com.intellij.execution.junit2.info.ClassBasedInfo;
import com.intellij.execution.junit2.info.DisplayTestInfoExtractor;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.util.containers.HashMap;

import java.util.List;

public class TestRootImpl implements TestRoot {
  private final TestProxy myRootTest;
  private final HashMap<String,TestProxy> myKnownDynamicParents = new HashMap<String, TestProxy>();

  public TestRootImpl(final TestProxy rootTest) {
    myRootTest = rootTest;
  }

  public void addChild(final TestProxy child) {
    if (child == myRootTest)
      return;
    getDynamicParentFor(child).addChild(child);
  }

  private TestProxy getDynamicParentFor(final TestProxy child) {
    final String parentClass = child.getInfo().getComment();
    TestProxy dynamicParent = myKnownDynamicParents.get(parentClass);
    if (dynamicParent == null) {
      dynamicParent = new TestProxy(new DynamicParentInfo(parentClass));
      myKnownDynamicParents.put(parentClass, dynamicParent);
      myRootTest.addChild(dynamicParent);
    }
    return dynamicParent;
  }

  public TestProxy getRootTest() {
    return myRootTest;
  }

  public List<TestProxy> getAllTests() {
    return getRootTest().getAllTests();
  }

  private static class DynamicParentInfo extends ClassBasedInfo {
    public DynamicParentInfo(final String className) {
      super(DisplayTestInfoExtractor.FOR_CLASS);
      setClassName(className);
    }

    public void readPacketFrom(final ObjectReader reader) {
    }
  }
}
