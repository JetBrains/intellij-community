// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Proxy class that allows to avoid a hard compile time dependency on the XPathView plugin.
 */
public abstract class JspSupportProxy {

  public abstract String @NotNull [] getPossibleTldUris(final Module module);

  private static JspSupportProxy ourInstance;
  private static boolean isInitialized;

  @Nullable
  public static synchronized JspSupportProxy getInstance() {
    if (isInitialized) {
      return ourInstance;
    }
    try {
      return ourInstance = ApplicationManager.getApplication().getService(JspSupportProxy.class);
    } finally {
      isInitialized = true;
    }
  }
}