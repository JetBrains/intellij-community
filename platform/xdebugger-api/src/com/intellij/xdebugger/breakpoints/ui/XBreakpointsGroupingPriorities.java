// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.breakpoints.ui;

public final class XBreakpointsGroupingPriorities {
  public static final int DEFAULT = 100;
  public static final int BY_CLASS = 400;
  public static final int BY_FILE = 600;
  public static final int BY_PACKAGE = 800;
  public static final int BY_TYPE = 1000;
}
