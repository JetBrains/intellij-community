// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public class EmptyRunnable implements Runnable {
  public static final Runnable INSTANCE = new EmptyRunnable();

  public static Runnable getInstance() {
    return INSTANCE;
  }

  @Override
  public void run() { }

  @Override
  public String toString() {
    return "EmptyRunnable";
  }
}