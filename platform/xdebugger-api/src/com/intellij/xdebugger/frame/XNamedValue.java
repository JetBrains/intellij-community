// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public abstract class XNamedValue extends XValue {
  
  protected final @NlsSafe String myName;

  protected XNamedValue(@NotNull String name) {
    myName = name;
  }

  public final @NlsSafe @NotNull String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return getName();
  }
}
