// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;


public final class SeparatorOrientation {
  public static final SeparatorOrientation HORIZONTAL = new SeparatorOrientation("HORIZONTAL");
  public static final SeparatorOrientation VERTICAL = new SeparatorOrientation("VERTICAL");

  private final String myName; // for debug only

  private SeparatorOrientation(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

}