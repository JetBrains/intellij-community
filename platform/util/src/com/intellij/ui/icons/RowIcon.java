// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.List;

public interface RowIcon extends CompositeIcon, DarkIconProvider {
  enum Alignment {TOP, CENTER, BOTTOM}

  void setIcon(Icon icon, int i);

  @NotNull @Unmodifiable
  List<Icon> getAllIcons();
}
