// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Proxy class that allows to avoid a hard compile time dependency on the XPathView plugin.
 */
public abstract class XPathSupportProxy {
  public abstract @NotNull XPath createXPath(String expression) throws JaxenException;

  public abstract void attachContext(@NotNull PsiFile file);

  public static synchronized @Nullable XPathSupportProxy getInstance() {
    return ApplicationManager.getApplication().getService(XPathSupportProxy.class);
  }
}