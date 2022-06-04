// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register the implementation as {@code com.intellij.usageTypeProvider} extension in plugin.xml
 * to provide usage types.
 *
 * @see UsageType
 * @see UsageTypeProviderEx
 */
public interface UsageTypeProvider {

  @Internal ExtensionPointName<UsageTypeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.usageTypeProvider");

  /**
   * @param element underlying usage element, usually {@link UsageInfo#getElement()}
   * @return usage type of the {@code element},
   * or {@code null} if this provider cannot classify the {@code element}
   */
  @Nullable UsageType getUsageType(@NotNull PsiElement element);
}
