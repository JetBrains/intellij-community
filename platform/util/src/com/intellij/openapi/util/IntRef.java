// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public final class IntRef {
  private int myValue;

  public IntRef() {
    this(0);
  }

  public IntRef(int value) {
    myValue = value;
  }

  public int get() {
    return myValue;
  }

  public void set(int value) {
    myValue = value;
  }

  public void inc() {
    myValue++;
  }

  @Override
  public String toString() {
    return "IntRef(" + myValue + ")";
  }
}
