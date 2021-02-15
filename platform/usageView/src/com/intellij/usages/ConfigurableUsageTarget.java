// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface ConfigurableUsageTarget extends UsageTarget {

  void showSettings();

  KeyboardShortcut getShortcut();

  @Nls @NotNull String getLongDescriptiveName();
}
