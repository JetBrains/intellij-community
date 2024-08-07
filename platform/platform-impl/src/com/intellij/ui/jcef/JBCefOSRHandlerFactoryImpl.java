// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JBCefOSRHandlerFactoryImpl implements JBCefOSRHandlerFactory {
  @Override
  @NotNull
  public JComponent createComponent(boolean isMouseWheelEventEnabled) {
    return new JBCefOsrComponent(isMouseWheelEventEnabled);
  }

  @Override
  public @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
    assert component instanceof JBCefOsrComponent;
    JBCefOsrComponent osrComponent = (JBCefOsrComponent)component;
    JBCefOsrHandler handler = JBCefApp.isRemoteEnabled() ? new JBCefNativeOsrHandler() : new JBCefOsrHandler();
    osrComponent.setRenderHandler(handler);

    return handler;
  }
}
