// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scroll.TouchScrollUtil;
import com.intellij.util.Alarm;
import com.jetbrains.cef.JCefAppConfig;
import org.cef.browser.CefBrowser;
import org.cef.input.CefTouchEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputMethodRequests;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A lightweight component on which an off-screen browser is rendered.
 *
 * @see JBCefBrowser#getComponent()
 * @see JBCefOsrHandler
 * @author tav
 */
@SuppressWarnings("NotNullFieldNotInitialized")
class JBCefOsrComponent extends JPanel {
  static final int RESIZE_DELAY_MS = Integer.getInteger("ide.browser.jcef.resize_delay_ms", 100);
  private volatile @NotNull JBCefOsrHandler myRenderHandler;
  private volatile @NotNull CefBrowser myBrowser;
  private final @NotNull JBCefInputMethodAdapter myInputMethodAdapter = new JBCefInputMethodAdapter(this);

  private double myScale = 1.0;

  private final @NotNull AtomicLong myScheduleResizeMs = new AtomicLong(-1);
  private @Nullable Alarm myResizeAlarm;

  private final @NotNull Alarm myGraphicsConfigurationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  AtomicBoolean myScaleInitialized = new AtomicBoolean(false);

  private @NotNull Disposable myDisposable;

  private final int WHEEL_ROTATION_FACTOR = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor");

  JBCefOsrComponent(boolean isMouseWheelEventEnabled) {
    setPreferredSize(JBCefBrowser.DEF_PREF_SIZE);
    setBackground(JBColor.background());

    enableEvents(AWTEvent.KEY_EVENT_MASK |
                 AWTEvent.MOUSE_EVENT_MASK |
                 (isMouseWheelEventEnabled ? AWTEvent.MOUSE_WHEEL_EVENT_MASK : 0L) |
                 AWTEvent.MOUSE_MOTION_EVENT_MASK |
                 AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);

    setFocusable(true);
    setRequestFocusEnabled(true);
    setFocusTraversalKeysEnabled(false);

    addInputMethodListener(myInputMethodAdapter);

    // This delay is a workaround for JBR-7335.
    // After the device configuration is changed, the browser reacts to it whether it receives a notification from the client or not.
    // An additional notification during this time can break the internal state of the browser, which leads to the picture freeze.
    // The purpose of this delay is to give the browser a chance to handle the graphics configuration change before we update the scale on
    // our side.
    // The first graphicsConfiguration call is caused by the adding the browser component and doesn't need to be delayed.
    // Further calls might be caused by the hardware setup or resolution changes.
    addPropertyChangeListener("graphicsConfiguration", e -> {
      myGraphicsConfigurationAlarm.cancelAllRequests();
      if (myScaleInitialized.get()) {
        myGraphicsConfigurationAlarm.addRequest(this::onGraphicsConfigurationChanged, 1000);
      }
      else {
        onGraphicsConfigurationChanged();
        myScaleInitialized.set(true);
      }
    });
  }

  public void setBrowser(@NotNull CefBrowser browser) {
    myBrowser = browser;
    myInputMethodAdapter.setBrowser(browser);
  }

  public void setRenderHandler(@NotNull JBCefOsrHandler renderHandler) {
    myRenderHandler = renderHandler;

    myRenderHandler.addCaretListener(myInputMethodAdapter);

    addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) { if (isShowing()) myRenderHandler.setLocationOnScreen(getLocationOnScreen()); }
    });

    try {
      myRenderHandler.setLocationOnScreen(getLocationOnScreen());
    } catch (IllegalComponentStateException t) {
      // The component isn't shown
    }

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myRenderHandler.setLocationOnScreen(getLocationOnScreen());
      }
    });
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myDisposable = Disposer.newDisposable();
    myResizeAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);

    ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(UISettingsListener.TOPIC, uiSettings -> {
      onGraphicsConfigurationChanged();
    });

    if (!JBCefBrowserBase.isCefBrowserCreationStarted(myBrowser)) {
      myBrowser.createImmediately();
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Disposer.dispose(myDisposable);

    myGraphicsConfigurationAlarm.cancelAllRequests();
    myScaleInitialized.set(false);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myRenderHandler.paint((Graphics2D)g);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reshape(int x, int y, int w, int h) {
    super.reshape(x, y, w, h);
    final long timeMs = System.currentTimeMillis();
    if (myResizeAlarm != null) {
      if (myResizeAlarm.isEmpty())
        myScheduleResizeMs.set(timeMs);
      myResizeAlarm.cancelAllRequests();
      if (timeMs - myScheduleResizeMs.get() > RESIZE_DELAY_MS)
        myBrowser.wasResized(0, 0);
      else
        myResizeAlarm.addRequest(() -> {
          // In OSR width and height are ignored. The view size will be requested from CefRenderHandler.
          myBrowser.wasResized(0, 0);
        }, RESIZE_DELAY_MS);
    }
  }

  @Override
  public InputMethodRequests getInputMethodRequests() {
    return myInputMethodAdapter;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    if (e.isConsumed()) {
      return;
    }

    myBrowser.sendMouseEvent(new MouseEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      ROUND.round(e.getX() / myScale),
      ROUND.round(e.getY() / myScale),
      ROUND.round(e.getXOnScreen() / myScale),
      ROUND.round(e.getYOnScreen() / myScale),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getButton()));

    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      requestFocusInWindow();
    }
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    super.processMouseWheelEvent(e);
    if (e.isConsumed()) {
      return;
    }


    if (TouchScrollUtil.isTouchScroll(e)) {
      myBrowser.sendTouchEvent(new CefTouchEvent(0, e.getX(), e.getY(), 0, 0, 0, 0, getTouchEventType(e), e.getModifiersEx(),
                                                 CefTouchEvent.PointerType.UNKNOWN));
    }
    else {
      double val = e.getPreciseWheelRotation() * WHEEL_ROTATION_FACTOR;
      if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
        val *= -1;
      }
      myBrowser.sendMouseWheelEvent(new MouseWheelEvent(
        e.getComponent(),
        e.getID(),
        e.getWhen(),
        e.getModifiersEx(),
        ROUND.round(e.getX() / myScale),
        ROUND.round(e.getY() / myScale),
        ROUND.round(e.getXOnScreen() / myScale),
        ROUND.round(e.getYOnScreen() / myScale),
        e.getClickCount(),
        e.isPopupTrigger(),
        e.getScrollType(),
        e.getScrollAmount(),
        (int)val,
        val));
    }
  }

  static CefTouchEvent.EventType getTouchEventType(MouseWheelEvent e) {
    if (!TouchScrollUtil.isTouchScroll(e)) return null;

    if (TouchScrollUtil.isBegin(e)) {
      return CefTouchEvent.EventType.PRESSED;
    }
    else if (TouchScrollUtil.isUpdate(e)) {
      return CefTouchEvent.EventType.MOVED;
    }
    else if (TouchScrollUtil.isEnd(e)) {
      return CefTouchEvent.EventType.RELEASED;
    }

    return CefTouchEvent.EventType.CANCELLED;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);

    myBrowser.sendMouseEvent(new MouseEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      ROUND.round(e.getX() / myScale),
      ROUND.round(e.getY() / myScale),
      ROUND.round(e.getXOnScreen() / myScale),
      ROUND.round(e.getYOnScreen() / myScale),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getButton()));
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
    myBrowser.sendKeyEvent(e);
  }

  private void onGraphicsConfigurationChanged() {
    double oldScale = myScale;
    double oldDensity = myRenderHandler.getPixelDensity();
    double pixelDensity = JreHiDpiUtil.isJreHiDPIEnabled() ? JCefAppConfig.getDeviceScaleFactor(this) : 1.0;
    myScale = (JreHiDpiUtil.isJreHiDPIEnabled() ? 1.0 : JCefAppConfig.getDeviceScaleFactor(this)) *
              UISettings.getInstance().getIdeScale();
    myRenderHandler.setScreenInfo(pixelDensity, myScale);
    if (oldScale != myScale || oldDensity != pixelDensity) {
      myBrowser.notifyScreenInfoChanged();
    }
  }

  Color getColorAt(int x, int y) {
    return myRenderHandler.getColorAt(x, y);
  }

  double getPixelDensity() {
    return myRenderHandler.getPixelDensity();
  }
}
