// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface UpdatableIcon extends Icon, ModificationTracker {
  /** Notify about painting was done on the c component*/
  void notifyPaint(@NotNull Component c, int x, int y);
}
