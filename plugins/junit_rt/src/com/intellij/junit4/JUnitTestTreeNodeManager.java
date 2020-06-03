// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.runner.Description;

public interface JUnitTestTreeNodeManager {
  String JUNIT_TEST_TREE_NODE_MANAGER_ARGUMENT = "nodeNamesHandler";

  TestNodePresentation getRootNodePresentation(String fullName);

  String getNodeName(String fqName, boolean splitBySlash);

  String getTestLocation(Description description, String className, String methodName);

  JUnitTestTreeNodeManager JAVA_NODE_NAMES_MANAGER = new JUnitTestTreeNodeManager() {
    @Override
    public TestNodePresentation getRootNodePresentation(String fullName) {
      if (fullName == null) {
        return new TestNodePresentation(null, null);
      }
      int lastPointIdx = fullName.lastIndexOf('.');
      String name = fullName;
      String comment = null;
      if (lastPointIdx >= 0) {
        name = fullName.substring(lastPointIdx + 1);
        comment = fullName.substring(0, lastPointIdx);
      }
      return new TestNodePresentation(name, comment);
    }

    @Override
    public String getNodeName(String fqName, boolean splitBySlash) {
      if (fqName == null) return null;
      final int idx = fqName.indexOf("[");
      if (idx == 0) {
        //param name
        return fqName;
      }
      String fqNameWithoutParams = idx > 0 && fqName.endsWith("]") ? fqName.substring(0, idx) : fqName;
      int classEnd = splitBySlash ? fqNameWithoutParams.indexOf('/') : -1;
      if (classEnd >= 0) {
        return fqName.substring(classEnd + 1);
      }

      int dotInClassFQNIdx = fqNameWithoutParams.lastIndexOf('.');
      return dotInClassFQNIdx > -1 ? fqName.substring(dotInClassFQNIdx + 1) : fqName;
    }

    @Override
    public String getTestLocation(Description description, String className, String methodName) {
      return "locationHint='java:test://" +
             MapSerializerUtil.escapeStr(className + "/" + getNodeName(methodName, true), MapSerializerUtil.STD_ESCAPER) +
             "'";
    }
  };

  class TestNodePresentation {
    private final String myName;

    private final String myComment;

    public TestNodePresentation(String name, String comment) {
      myName = name;
      myComment = comment;
    }

    public String getName() {
      return myName;
    }

    public String getComment() {
      return myComment;
    }
  }
}
