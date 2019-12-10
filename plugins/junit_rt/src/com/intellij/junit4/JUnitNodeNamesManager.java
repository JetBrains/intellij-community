// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

public interface JUnitNodeNamesManager {
  String JUNIT_NODE_NAMES_MANAGER_ARGUMENT = "nodeNamesHandler";
  String TEXT_NODE_NAMES_MANAGER_NAME = "AsText";

  TestNodePresentation getRootNodePresentation(String fullName);

  String getNodeName(String fqName, boolean splitBySlash);

  JUnitNodeNamesManager JAVA_NODE_NAMES_MANAGER = new JUnitNodeNamesManager() {
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
  };

  JUnitNodeNamesManager SIMPLE_NODE_NAMES_MANAGER = new JUnitNodeNamesManager() {
    public JUnitNodeNamesManager.TestNodePresentation getRootNodePresentation(String fullName) {
      return new JUnitNodeNamesManager.TestNodePresentation(fullName, null);
    }

    public String getNodeName(String fqName, boolean splitBySlash) {
      return fqName;
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
