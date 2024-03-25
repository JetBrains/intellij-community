// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.util.Function;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JBCefOSRHandlerFactoryImpl implements JBCefOSRHandlerFactory {
  @Override
  @NotNull
  public CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
    assert component instanceof JBCefOsrComponent;
    JBCefOsrComponent osrComponent = (JBCefOsrComponent)component;
    Function<? super JComponent, ? extends Rectangle> screenBoundsProvider = createScreenBoundsProvider();
    return JBCefApp.isRemoteEnabled() ?
           new JBCefNativeOsrHandler(osrComponent, screenBoundsProvider)
           : new JBCefOsrHandler(osrComponent, screenBoundsProvider);
  }
}
