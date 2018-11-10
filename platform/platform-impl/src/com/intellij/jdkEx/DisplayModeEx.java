// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Experimental
public interface DisplayModeEx {
  boolean isDefault(@NotNull DisplayMode dm);
}

class DefDisplayModeEx implements DisplayModeEx {
  @Override
  public boolean isDefault(@NotNull DisplayMode dm) {
    return true;
  }
}

class JBDisplayModeEx implements DisplayModeEx {
  private static final MethodInvocator isDefaultInvocator = new MethodInvocator(DisplayMode.class, "isDefault", null);

  @Override
  public boolean isDefault(@NotNull DisplayMode dm) {
    return isDefaultInvocator.isAvailable() ? (Boolean)isDefaultInvocator.invoke(dm) : true;
  }
}
