// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefRequestContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Experimental
public interface CefOsrBrowserFactory {
  @NotNull CefBrowser createOsrBrowser(
    @NotNull JBCefOSRHandlerFactory osrHandlerFactory,
    @NotNull CefClient client,
    @Nullable String url,
    @Nullable CefRequestContext context,
    // not-null parentBrowser creates a DevTools browser for it
    @Nullable CefBrowser parent,
    @Nullable Point inspectAt,
    boolean isMouseWheelEventEnabled,
    CefBrowserSettings settings
  );

  static CefOsrBrowserFactory getInstance() {
    return ApplicationManager.getApplication().getService(CefOsrBrowserFactory.class);
  }
}
