// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public abstract class XNamedValue extends XValue {
  
  @NlsSafe
  protected final String myName;

  protected XNamedValue(@NotNull String name) {
    myName = name;
  }

  @NlsSafe
  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return getName();
  }
}
