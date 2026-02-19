// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.psi.PsiElement;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageTypeProviderEx extends UsageTypeProvider {
  @Nullable UsageType getUsageType(@NotNull PsiElement element, @NotNull UsageTarget @NotNull [] targets);
}
