// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.maddyhome.idea.copyright.pattern;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public final class CopyrightVariablesProviders extends FileTypeExtension<CopyrightVariablesProvider> {
  public static final CopyrightVariablesProviders INSTANCE = new CopyrightVariablesProviders();

  private CopyrightVariablesProviders() {
    super("com.intellij.copyright.variablesProvider");
  }
}
