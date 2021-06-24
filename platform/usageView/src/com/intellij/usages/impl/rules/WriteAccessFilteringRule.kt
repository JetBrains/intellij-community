// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageFilteringRule;
import org.jetbrains.annotations.NotNull;

public class WriteAccessFilteringRule implements UsageFilteringRule {

  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    if (usage instanceof ReadWriteAccessUsage) {
      return ((ReadWriteAccessUsage)usage).isAccessedForReading();
    }
    return true;
  }
}
