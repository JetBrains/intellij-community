// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionListener;

public final class TimerUtil {
  @NotNull
  public static Timer createNamedTimer(@NonNls @NotNull String name, int delay, @NotNull ActionListener listener) {
    return new Timer(delay, listener) {
      @Override
      public String toString() {
        return name;
      }
    };
  }

  @NotNull
  public static Timer createNamedTimer(@NonNls @NotNull String name, int delay) {
    return new Timer(delay, null) {
      @Override
      public String toString() {
        return name;
      }
    };
  }
}
