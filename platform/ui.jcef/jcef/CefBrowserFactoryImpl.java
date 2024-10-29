// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.util.ObjectUtils;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.browser.CefRendering;
import org.cef.browser.CefRequestContext;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class CefBrowserFactoryImpl implements CefOsrBrowserFactory {
  private static CefRendering.CefRenderingWithHandler createCefRenderingWithHandler(@NotNull JBCefOSRHandlerFactory osrHandlerFactory, boolean isMouseWheelEventEnabled) {
    JComponent component = osrHandlerFactory.createComponent(isMouseWheelEventEnabled);
    CefRenderHandler handler = osrHandlerFactory.createCefRenderHandler(component);
    return new CefRendering.CefRenderingWithHandler(handler, component);
  }

  @Override
  public @NotNull CefBrowser createOsrBrowser(@NotNull JBCefOSRHandlerFactory osrHandlerFactory,
                                              @NotNull CefClient client,
                                              @Nullable String url,
                                              @Nullable CefRequestContext context,
                                              @Nullable CefBrowser parentBrowser,
                                              @Nullable Point inspectAt,
                                              boolean isMouseWheelEventEnabled,
                                              CefBrowserSettings settings) {
    if (JBCefApp.isRemoteEnabled()) {
      CefBrowser browser = null;
      try {
        // Use latest API via reflection to avoid jcef-version increment
        // TODO: remove reflection
        Supplier<CefRendering> renderingSupplier = () -> createCefRenderingWithHandler(osrHandlerFactory, isMouseWheelEventEnabled);
        Method m = CefClient.class.getMethod("createBrowser", String.class, Supplier.class, boolean.class, CefRequestContext.class, CefBrowserSettings.class);
        browser = (CefBrowser)m.invoke(client, url, renderingSupplier, true, context, settings);
      } catch (Throwable e) {}

      if (browser == null) {
        final CefRendering.CefRenderingWithHandler rendering = createCefRenderingWithHandler(osrHandlerFactory, isMouseWheelEventEnabled);
        browser = client.createBrowser(
          ObjectUtils.notNull(url, ""),
          rendering,
          true /* isTransparent - unused*/,
          context);
      }

      if (browser.getUIComponent() instanceof JBCefOsrComponent)
        ((JBCefOsrComponent)browser.getUIComponent()).setBrowser(browser);

      return browser;
    }

    final CefRendering.CefRenderingWithHandler rendering = createCefRenderingWithHandler(osrHandlerFactory, isMouseWheelEventEnabled);
    CefBrowserOsrWithHandler browser =
      new CefBrowserOsrWithHandler(client, ObjectUtils.notNull(url, ""), context, rendering.getRenderHandler(), rendering.getComponent(), parentBrowser, inspectAt, settings) {
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

    if (rendering.getComponent() instanceof JBCefOsrComponent)
      ((JBCefOsrComponent)rendering.getComponent()).setBrowser(browser);
    return browser;
  }
}
