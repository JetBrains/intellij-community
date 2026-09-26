// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.FocusedComponentProvider;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

public final class JcefFocusedComponentProvider implements FocusedComponentProvider {
  @Override
  public @Nullable Component getFocusedComponent() {
    JBCefBrowserBase browser = JBCefBrowserBase.getFocusedBrowser();
    return browser == null ? null : browser.getComponent();
  }
}
