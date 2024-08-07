// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.util.ObjectUtils;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.*;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CefBrowserFactoryImpl implements CefOsrBrowserFactory {
  @Override
  public @NotNull CefBrowser createOsrBrowser(@NotNull JBCefOSRHandlerFactory osrHandlerFactory,
                                              @NotNull CefClient client,
                                              @Nullable String url,
                                              @Nullable CefRequestContext context,
                                              @Nullable CefBrowser parentBrowser,
                                              @Nullable Point inspectAt,
                                              boolean isMouseWheelEventEnabled,
                                              CefBrowserSettings settings) {
    JComponent comp = osrHandlerFactory.createComponent(isMouseWheelEventEnabled);
    CefRenderHandler handler = osrHandlerFactory.createCefRenderHandler(comp);
    if (JBCefApp.isRemoteEnabled()) {
      CefBrowser browser = client.createBrowser(
        ObjectUtils.notNull(url, ""),
        new CefRendering.CefRenderingWithHandler(handler, comp),
        true /* isTransparent - unused*/,
        context);

      if (comp instanceof JBCefOsrComponent) {
        ((JBCefOsrComponent)comp).setBrowser(browser);
      }

      return browser;
    }

    CefBrowserOsrWithHandler browser =
      new CefBrowserOsrWithHandler(client, ObjectUtils.notNull(url, ""), context, handler, comp, parentBrowser, inspectAt, settings) {
        @Override
        protected CefBrowser createDevToolsBrowser(CefClient client,
                                                   String url,
                                                   CefRequestContext context,
                                                   CefBrowser parent,
                                                   Point inspectAt) {
          return createOsrBrowser(osrHandlerFactory, client, getUrl(), getRequestContext(), this, inspectAt, isMouseWheelEventEnabled,
                                  null);
        }
      };

    if (comp instanceof JBCefOsrComponent) ((JBCefOsrComponent)comp).setBrowser(browser);
    return browser;
  }
}
