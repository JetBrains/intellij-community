// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.misc.CefRange;
import org.cef.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;


abstract class JBCefOsrHandlerBase implements CefRenderHandler {
  private final @NotNull AtomicReference<Point> myLocationOnScreenRef = new AtomicReference<>(new Point());
  private volatile @Nullable JBCefCaretListener myCaretListener;

  // DIP (device independent pixel aka logic pixel) size in screen pixels. Expected to be != 1 only if JRE supports HDPI
  private volatile double myPixelDensity = 1;
  private volatile double myScaleFactor = 1;

  public void setScreenInfo(double pixelDensity, double scaleFactor) {
    myPixelDensity = pixelDensity;
    myScaleFactor = scaleFactor;
  }

  protected double getPixelDensity() { return myPixelDensity; }

  protected double getScaleFactor() { return myScaleFactor; }

  @Override
  public Rectangle getViewRect(CefBrowser browser) {
    Component component = browser.getUIComponent();
    double scale = getScaleFactor();
    double value = component.getWidth() / scale;
    double value1 = component.getHeight() / scale;
    return new Rectangle(0, 0, (int)Math.ceil(value), (int)Math.ceil(value1));
  }

  @Override
  public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
    Rectangle rect = getScreenBoundaries(browser.getUIComponent());
    double scale = myScaleFactor * myPixelDensity;
    screenInfo.Set(scale, 32, 4, false, rect, rect);
    return true;
  }

  @Override
  public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
    Point pt = viewPoint.getLocation();
    Point loc = myLocationOnScreenRef.get();
    if (OS.isMacintosh()) {
      Rectangle rect = getScreenBoundaries(browser.getUIComponent());
      pt.setLocation(loc.x + pt.x, rect.height - loc.y - pt.y);
    }
    else {
      pt.translate(loc.x, loc.y);
    }
    return OS.isMacintosh() ? pt : toRealCoordinates(pt);
  }

  @Override
  public double getDeviceScaleFactor(CefBrowser browser) {
    return myScaleFactor * myPixelDensity;
  }

  @Override
  public boolean onCursorChange(CefBrowser browser, int cursorType) {
    SwingUtilities.invokeLater(() -> browser.getUIComponent().setCursor(new Cursor(cursorType)));
    return true;
  }

  @Override
  public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
    return false;
  }

  @Override
  public void updateDragCursor(CefBrowser browser, int operation) {
  }

  @Override
  public void OnImeCompositionRangeChanged(CefBrowser browser, CefRange selectionRange, Rectangle[] characterBounds) {
    JBCefCaretListener listener = myCaretListener;
    if (listener != null) {
      listener.onImeCompositionRangeChanged(selectionRange, characterBounds);
    }
  }

  @Override
  public void OnTextSelectionChanged(CefBrowser browser, String selectedText, CefRange selectionRange) {
    JBCefCaretListener listener = myCaretListener;
    if (listener != null) {
      listener.onTextSelectionChanged(selectedText, selectionRange);
    }
  }

  public void setLocationOnScreen(Point location) {
    myLocationOnScreenRef.set(location);
  }

  private @NotNull Point toRealCoordinates(@NotNull Point pt) {
    double scale = getPixelDensity();
    return new Point((int)Math.round(pt.x * scale), (int)Math.round(pt.y * scale));
  }

  void addCaretListener(JBCefCaretListener listener) {
    myCaretListener = listener;
  }

  private static Rectangle getScreenBoundaries(Component component) {
    if (component != null && !GraphicsEnvironment.isHeadless()) {
      try {
        return component.isShowing() ?
               component.getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds() :
               GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
      }
      catch (Exception ignored) {
      }
    }

    return new Rectangle(0, 0, 0, 0);
  }
}
