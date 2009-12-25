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

    public void readFrom(final ObjectReader reader) {
    }
  }
}
