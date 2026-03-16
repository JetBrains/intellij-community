// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

// this extension point implementation is used for demoing and debugging JCEF from a standalone bundle
public class JBCefDefaultNativeBundleProvider implements JBCefNativeBundleProvider {
  private final String path;
  JBCefDefaultNativeBundleProvider() {
    path = System.getProperty("jcef.native.bundle.path", null);
  }

  @Override
  public String getNativeBundlePath() {
    return path;
  }

  @Override
  public boolean isAvailable() {
    return path != null;
  }
}
