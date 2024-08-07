// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.awt.geom.Point2D;
import java.awt.im.InputMethodRequests;
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
  private @Nullable Alarm myAlarm;
  private @NotNull Disposable myDisposable;
  private @NotNull MouseWheelEventsAccumulator myWheelEventsAccumulator;

  JBCefOsrComponent(boolean isMouseWheelEventEnabled) {
    setPreferredSize(JBCefBrowser.DEF_PREF_SIZE);
    setBackground(JBColor.background());
    addPropertyChangeListener("graphicsConfiguration",
                              e -> {
                                double pixelDensity = JreHiDpiUtil.isJreHiDPIEnabled() ? JCefAppConfig.getDeviceScaleFactor(this) : 1.0;
                                myScale = (JreHiDpiUtil.isJreHiDPIEnabled() ? 1.0 : JCefAppConfig.getDeviceScaleFactor(this)) *
                                          UISettings.getInstance().getIdeScale();
                                myRenderHandler.setScreenInfo(pixelDensity, myScale);
                                myBrowser.notifyScreenInfoChanged();
                              });

    enableEvents(AWTEvent.KEY_EVENT_MASK |
                 AWTEvent.MOUSE_EVENT_MASK |
                 (isMouseWheelEventEnabled ? AWTEvent.MOUSE_WHEEL_EVENT_MASK : 0L) |
                 AWTEvent.MOUSE_MOTION_EVENT_MASK |
                 AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);

    setFocusable(true);
    setRequestFocusEnabled(true);
    setFocusTraversalKeysEnabled(false);
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myBrowser.setFocus(true);
      }
      @Override
      public void focusLost(FocusEvent e) {
        myBrowser.setFocus(false);
      }
    });

    addInputMethodListener(myInputMethodAdapter);
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
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    myWheelEventsAccumulator = new MouseWheelEventsAccumulator(myDisposable);

    ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(UISettingsListener.TOPIC, uiSettings -> {
      double pixelDensity = JreHiDpiUtil.isJreHiDPIEnabled() ? JCefAppConfig.getDeviceScaleFactor(this) : 1.0;
      myScale = (JreHiDpiUtil.isJreHiDPIEnabled() ? 1.0 : JCefAppConfig.getDeviceScaleFactor(this)) *
                uiSettings.getIdeScale();
      myRenderHandler.setScreenInfo(pixelDensity, myScale);
      myBrowser.notifyScreenInfoChanged();
    });

    if (!JBCefBrowserBase.isCefBrowserCreationStarted(myBrowser)) {
      myBrowser.createImmediately();
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Disposer.dispose(myDisposable);
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
    if (myAlarm != null) {
      if (myAlarm.isEmpty())
        myScheduleResizeMs.set(timeMs);
      myAlarm.cancelAllRequests();
      if (timeMs - myScheduleResizeMs.get() > RESIZE_DELAY_MS)
        myBrowser.wasResized(0, 0);
      else
        myAlarm.addRequest(() -> {
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
    } else {
      myWheelEventsAccumulator.push(e);
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

  /**
   * This class is an adapter between Java and CEF mouse wheel API.
   * CEF scrolling performance in some applications(e.g. PDF viewer) might be not enough to handle every java screen in time.
   * The purpose of this adapter is to reduce the number of events to handle by CEF.
   * MouseWheelEventsAccumulator accumulates wheel events (by X and Y axis independent) during the time defined by TIMEOUT_MS and sends
   * composed events to the browser on timeout or on reaching wheel rotation tolerance (defined by TOLERANCE).
   * <p>
   * In practice, this reduces the number of events by about two times
   */
  private class MouseWheelEventsAccumulator {
    private final Composition myCompositionX, myCompositionY;
    private final int wheelFactor = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor");
    public static final int TIMEOUT_MS = 500;
    public static final int TOLERANCE = 3;

    MouseWheelEventsAccumulator(Disposable parent) {
      myCompositionX = new Composition(parent);
      myCompositionY = new Composition(parent);
    }

    void push(MouseWheelEvent event) {
      if (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
        myBrowser.sendMouseWheelEvent(event);
        return;
      }

      Composition composition = event.isShiftDown() ? myCompositionX : myCompositionY;
      if (composition.lastEvent != null && !areHomogenous(composition.lastEvent, event)) {
        commit(composition);
        return;
      }

      if (composition.lastEvent == null) {
        composition.myAlarm.addRequest(() -> commit(composition), TIMEOUT_MS);
      }

      composition.pendingScrollAmount += event.getScrollAmount();
      composition.pendingPreciseWheelRotation += event.getPreciseWheelRotation();
      composition.pendingClickCount += event.getClickCount();
      composition.lastEvent = event;

      if (Math.abs(composition.pendingPreciseWheelRotation * wheelFactor) > TOLERANCE * myScale) {
        commit(composition);
      }
    }

    private void commit(Composition composition) {
      if (composition.lastEvent == null) return;

      double val = composition.pendingPreciseWheelRotation * wheelFactor;
      if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
        val *= -1;
      }
      myBrowser.sendMouseWheelEvent(new MouseWheelEvent(
        composition.lastEvent.getComponent(),
        composition.lastEvent.getID(),
        composition.lastEvent.getWhen(),
        composition.lastEvent.getModifiersEx(),
        ROUND.round(composition.lastEvent.getX() / myScale),
        ROUND.round(composition.lastEvent.getY() / myScale),
        ROUND.round(composition.lastEvent.getXOnScreen() / myScale),
        ROUND.round(composition.lastEvent.getYOnScreen() / myScale),
        composition.pendingClickCount,
        composition.lastEvent.isPopupTrigger(),
        composition.lastEvent.getScrollType(),
        composition.pendingScrollAmount,
        (int)val,
        val));
      composition.reset();
    }

    static private boolean areHomogenous(MouseWheelEvent e1, MouseWheelEvent e2) {
      if (e1 == null || e2 == null) return false;

      double distance = Point2D.distance(e1.getX(), e1.getY(), e2.getX(), e2.getY());
      return e1.getComponent() == e2.getComponent() &&
             e1.getID() == e2.getID() &&
             e1.getModifiersEx() == e2.getModifiersEx() &&
             e1.isPopupTrigger() == e2.isPopupTrigger() &&
             e1.getScrollType() == e2.getScrollType() &&
             distance < TOLERANCE;
    }

    private static class Composition {
      private int pendingClickCount = 0;
      private int pendingScrollAmount = 0;
      private double pendingPreciseWheelRotation = 0.0;
      private final Alarm myAlarm;
      private @Nullable MouseWheelEvent lastEvent;

      Composition(Disposable parent) {
        myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
      }

      void reset() {
        lastEvent = null;
        pendingPreciseWheelRotation = 0;
        pendingClickCount = 0;
        pendingScrollAmount = 0;
        myAlarm.cancelAllRequests();
      }
    }
  }
}
