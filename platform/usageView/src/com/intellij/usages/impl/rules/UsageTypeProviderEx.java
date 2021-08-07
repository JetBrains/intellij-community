// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface UsageTypeProviderEx extends UsageTypeProvider {
  @Nullable
  UsageType
  getUsageType(PsiElement element, UsageTarget @NotNull [] targets);
}
