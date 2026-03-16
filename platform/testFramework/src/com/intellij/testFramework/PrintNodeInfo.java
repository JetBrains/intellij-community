// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

public class PrintNodeInfo {
  public final Object UserObject;
  public final String NodeText;
  public final int ChildrenCount;

  public PrintNodeInfo(Object object, String text, int count) {
    UserObject = object;
    NodeText = text;
    ChildrenCount = count;
  }
}
