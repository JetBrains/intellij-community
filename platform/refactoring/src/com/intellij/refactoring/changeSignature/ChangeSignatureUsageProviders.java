// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.Nullable;

public class ChangeSignatureUsageProviders extends LanguageExtension<ChangeSignatureUsageProvider> {
  private static final ChangeSignatureUsageProviders INSTANCE = new ChangeSignatureUsageProviders();
  public ChangeSignatureUsageProviders() {
    super("com.intellij.changeSignature.usageProvider");
  }

  public static @Nullable ChangeSignatureUsageProvider findProvider(Language language) {
    return INSTANCE.forLanguage(language);
  }
}
