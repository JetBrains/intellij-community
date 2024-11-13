// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * A factory for creating alternative component/handler/bounds for an off-screen rendering browser.
 *
 * @see JBCefBrowserBuilder#setOSRHandlerFactory(JBCefOSRHandlerFactory)
 * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
 * @author tav
 */
public interface JBCefOSRHandlerFactory {
  static JBCefOSRHandlerFactory getInstance() {
    return ApplicationManager.getApplication().getService(JBCefOSRHandlerFactory.class);
  }

  /**
   * Creates a lightweight component on which the browser will be rendered.
   *
   * @see JBCefBrowserBase#getComponent
   *
   * @param isMouseWheelEventEnabled If {@code true}, the browser will intercept mouse wheel events. Otherwise, the browser won't react
   *                                 on scrolling, and the parent component will handle scroll events.
   */
  default @NotNull JComponent createComponent(boolean isMouseWheelEventEnabled) {
    return new JBCefOsrComponent(isMouseWheelEventEnabled);
  }

  /**
   * Creates the OSR handler.
   */
  default @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
    assert component instanceof JBCefOsrComponent;
    JBCefOsrComponent osrComponent = (JBCefOsrComponent)component;
    var screenBoundsProvider = createScreenBoundsProvider();
    JBCefOsrHandler handler = JBCefApp.isRemoteEnabled() ?
                              new JBCefNativeOsrHandler(osrComponent, screenBoundsProvider) :
                              new JBCefOsrHandler(osrComponent, screenBoundsProvider);
    osrComponent.setRenderHandler(handler);

    return handler;
  }

  /**
   * Creates a screen bounds provider which takes into account the passed component.
   * Override the method in the headless mode to provide meaningful screen bounds.
   *
   * @see GraphicsEnvironment#isHeadless
   */
  default @NotNull Function<? super JComponent, ? extends Rectangle> createScreenBoundsProvider() {
    return component -> {
      if (component != null && !GraphicsEnvironment.isHeadless()) {
        try {
          return component.isShowing() ?
                 component.getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds() :
                 GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        }
        catch (Exception e) {
          Logger.getInstance(JBCefOSRHandlerFactory.class).error(e);
        }
      }

      return new Rectangle(0, 0, 0, 0);
    };
  }
}
