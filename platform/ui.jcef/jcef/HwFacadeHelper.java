// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.jbr.JdkEx;
import com.intellij.util.FieldAccessor;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.VolatileImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Provides a heavyweight window "facade" for a lightweight component.
 * Used to work around the problem of overlapping a heavyweight JCEF browser component.
 * The "facade" means that a hw window follows the lifecycle of the target lw component
 * (add/show/hide/dispose), follows its bounds and intercepts its painting.
 * The hw window itself is non-focusable and mouse-transparent, so the target lw
 * component handles mouse events as usual.
 *
 * @author tav
 */
@ApiStatus.Experimental
public class HwFacadeHelper {
  @SuppressWarnings("UseJBColor")
  public static final Color TRANSPARENT_COLOR = new Color(1, 1, 1, 0);

  private final @NotNull JComponent myTarget;
  private JWindow myHwFacade;
  private ComponentAdapter myOwnerListener;
  private ComponentAdapter myTargetListener;
  private VolatileImage myBackBuffer;

  @NotNull Consumer<? super JBCefBrowser> onBrowserMoveResizeCallback = browser -> {
    if (!browser.isOffScreenRendering()) activateIfNeeded(Collections.singletonList(browser.getCefBrowser()));
  };

  // [tav] todo: export visible browser bounds from jcef instead
  private static final class JCEFAccessor {
    private static FieldAccessor<CefApp, Set<CefClient>> clientsField;
    private static FieldAccessor<CefClient, Map<Integer, CefBrowser>> browsersField;
    private static CefApp ourCefApp;

    public static @Nullable CefApp getCefApp() {
      if (ourCefApp == null && CefApp.getState() != CefApp.CefAppState.NONE) {
        ourCefApp = CefApp.getInstance();
        //noinspection unchecked,rawtypes
        clientsField = new FieldAccessor(CefApp.class, "clients_", HashSet.class);
        //noinspection unchecked,rawtypes
        browsersField = new FieldAccessor(CefClient.class, "browser_", ConcurrentHashMap.class);
      }
      return ourCefApp;
    }

    public static @NotNull List<CefBrowser> getHwBrowsers() {
      List<CefBrowser> list = new LinkedList<>();
      if (getCefApp() == null || !clientsField.isAvailable() || !browsersField.isAvailable()) {
        return list;
      }

      Set<CefClient> clients;
      synchronized (ourCefApp) {
        clients = clientsField.get(ourCefApp);
        if (clients == null) {
          return list;
        }
        clients = new HashSet<>(clients);
      }
      for (CefClient client : clients) {
        Map<?, CefBrowser> browsers = browsersField.get(client);
        if (browsers == null) return list;
        for (CefBrowser browser : browsers.values()) {
          JBCefBrowserBase jbCefBrowser = JBCefBrowserBase.getJBCefBrowser(browser);
          if (jbCefBrowser != null && !jbCefBrowser.isOffScreenRendering()) {
            list.add(browser);
          }
        }
      }
      return list;
    }
  }

  public static HwFacadeHelper create(@NotNull JComponent target) {
    return JBCefApp.isSupported() ?
      new HwFacadeHelper(target) :
      // do not provoke any JBCef* class loading
      new HwFacadeHelper(target) {
        @Override
        public void addNotify() {
        }
        @Override
        public void show() {
        }
        @Override
        public void removeNotify() {
        }
        @Override
        public void hide() {
        }
        @Override
        public void paint(@NotNull Graphics g, @NotNull Consumer<? super Graphics> targetPaint) {
          targetPaint.accept(g);
        }
      };
  }

  private HwFacadeHelper(@NotNull JComponent target) {
    myTarget = target;
  }

  private boolean isActive() {
    return myHwFacade != null && Registry.is("ide.browser.jcef.hwfacade.enabled");
  }

  private static boolean isCefAppActive() {
    return JCEFAccessor.getCefApp() != null;
  }

  private void onShowing() {
    if (!isCefAppActive()) return;

    assert myHwFacade == null;
    assert myTarget.isVisible();

    myTarget.addComponentListener(myTargetListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        if (isActive()) {
          myHwFacade.setSize(myTarget.getSize());
        }
        else {
          activateIfNeeded(JCEFAccessor.getHwBrowsers());
        }
      }
      @Override
      public void componentMoved(ComponentEvent e) {
        if (isActive()) {
          if (myHwFacade.isVisible()) myHwFacade.setLocation(myTarget.getLocationOnScreen());
        }
        else {
          activateIfNeeded(JCEFAccessor.getHwBrowsers());
        }
      }
    });

    activateIfNeeded(JCEFAccessor.getHwBrowsers());
  }

  private void activateIfNeeded(@NotNull List<? extends CefBrowser> browsers) {
    if (isActive() || !Registry.is("ide.browser.jcef.hwfacade.enabled") || !isCefAppActive() || !myTarget.isShowing() || SystemInfo.isLinux) {
      return;
    }

    Rectangle targetBounds = new Rectangle(myTarget.getLocationOnScreen(), myTarget.getSize());
    boolean overlaps = false;
    // [tav] todo: still won't work for JCEF component in a popup above another popup, need a smarter and faster way to check z-order
    for (CefBrowser browser : browsers) {
      Component browserComp = browser.getUIComponent();
      if (browserComp != null && browserComp.isVisible() && browserComp.isShowing() &&
          !SwingUtilities.isDescendingFrom(browserComp, myTarget) &&
          new Rectangle(browserComp.getLocationOnScreen(), browserComp.getSize()).intersects(targetBounds))
      {
        overlaps = true;
        break;
      }
    }
    if (overlaps) {
      Window owner = SwingUtilities.getWindowAncestor(myTarget);
      owner.addComponentListener(myOwnerListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
          if (myTarget.isVisible()) {
            myHwFacade.setLocation(myTarget.getLocationOnScreen());
          }
        }
      });
      myHwFacade = new JWindow(owner);
      myHwFacade.add(new JPanel() {
        {
          setBackground(TRANSPARENT_COLOR);
        }
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (myBackBuffer != null) {
            g.drawImage(myBackBuffer, 0, 0, null);
          }
        }
      });
      JdkEx.setIgnoreMouseEvents(myHwFacade, true);
      myHwFacade.setBounds(targetBounds);
      myHwFacade.setFocusableWindowState(false);
      JdkEx.setTransparent(myHwFacade);
      myHwFacade.setVisible(true);
    }
  }

  public void addNotify() {
    if (myTarget.isVisible()) {
      onShowing();
    }
    if (!SystemInfo.isLinux) JBCefBrowser.addOnBrowserMoveResizeCallback(onBrowserMoveResizeCallback);
  }

  public void show() {
    if (!isCefAppActive()) return;

    if (AWTAccessor.getComponentAccessor().getPeer(myTarget) != null) {
      if (isActive()) {
        myHwFacade.setVisible(true);
      }
      else {
        onShowing();
      }
    }
  }

  public void removeNotify() {
    if (isActive()) {
      myHwFacade.dispose();
      myHwFacade = null;
      myBackBuffer = null;
      myTarget.removeComponentListener(myTargetListener);
      Window owner = SwingUtilities.getWindowAncestor(myTarget);
      assert owner != null;
      owner.removeComponentListener(myOwnerListener);
    }
    if (!SystemInfo.isLinux) JBCefBrowser.removeOnBrowserMoveResizeCallback(onBrowserMoveResizeCallback);
  }

  public void hide() {
    if (isActive()) {
      myHwFacade.setVisible(false);
    }
  }

  public void paint(@NotNull Graphics g, @NotNull Consumer<? super Graphics> targetPaint) {
    if (isActive()) {
      Dimension size = myTarget.getSize();
      if (myBackBuffer == null || myBackBuffer.getWidth() != size.width || myBackBuffer.getHeight() != size.height) {
        myBackBuffer = GraphicsEnvironment.
          getLocalGraphicsEnvironment().
          getDefaultScreenDevice().
          getDefaultConfiguration().
          createCompatibleVolatileImage(size.width, size.height, Transparency.TRANSLUCENT);
      }
      Graphics2D bbGraphics = (Graphics2D)myBackBuffer.getGraphics();
      bbGraphics.setBackground(TRANSPARENT_COLOR);
      bbGraphics.clearRect(0, 0, size.width, size.height);
      targetPaint.accept(bbGraphics);
      myHwFacade.repaint();
    }
    else {
      targetPaint.accept(g);
    }
  }
}
