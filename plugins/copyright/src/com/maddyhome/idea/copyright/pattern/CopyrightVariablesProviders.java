// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.pattern;

import com.intellij.openapi.fileTypes.FileTypeExtension;

public final class CopyrightVariablesProviders extends FileTypeExtension<CopyrightVariablesProvider> {
  public final static CopyrightVariablesProviders INSTANCE = new CopyrightVariablesProviders();

  private CopyrightVariablesProviders() {
    super("com.intellij.copyright.variablesProvider");
  }
}
