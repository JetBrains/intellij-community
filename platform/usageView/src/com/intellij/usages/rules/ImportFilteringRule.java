// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;

public abstract class ImportFilteringRule {

  public static final ExtensionPointName<ImportFilteringRule> EP_NAME = ExtensionPointName.create("com.intellij.importFilteringRule");

  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    return isVisible(usage);
  }

  public boolean isVisible(@NotNull Usage usage) {
    throw new AbstractMethodError("isVisible(Usage) or isVisible(Usage, UsageTarget[]) must be implemented");
  }
}
