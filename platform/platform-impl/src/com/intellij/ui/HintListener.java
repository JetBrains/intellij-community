// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.EventObject;

public interface HintListener extends EventListener{
  void hintHidden(@NotNull EventObject event);
}
