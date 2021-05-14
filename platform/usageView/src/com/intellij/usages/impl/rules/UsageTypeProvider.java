// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageTypeProvider {

  @Internal ExtensionPointName<UsageTypeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.usageTypeProvider");

  @Nullable UsageType getUsageType(@NotNull PsiElement element);
}
