// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.util.SystemInfo;
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
import java.util.function.Consumer;

/**
 * Provides a heavyweight window "facade" for a lightweight component.
 * Used to workaround the problem of overlapping a heavyweight JCEF browser component.
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

  private final JComponent myTarget;
  private JWindow myHwFacade;
  private ComponentAdapter myOwnerListener;
  private ComponentAdapter myTargetListener;
  private VolatileImage myBackBuffer;

  // [tav] todo: export visible browser bounds from jcef instead
  private static final class JCEFAccessor {
    private static FieldAccessor<CefApp, HashSet<CefClient>> clientsField;
    private static FieldAccessor<CefClient, HashMap<Integer, CefBrowser>> browsersField;
    private static CefApp ourCefApp;

    @Nullable
    public static CefApp getCefApp() {
      if (ourCefApp == null && CefApp.getState() != CefApp.CefAppState.NONE) {
        ourCefApp = CefApp.getInstance();
        clientsField = new FieldAccessor<>(CefApp.class, "clients_");
        browsersField = new FieldAccessor<>(CefClient.class, "browser_");
      }
      return ourCefApp;
    }

    @NotNull
    public static List<CefBrowser> getBrowsers() {
      List<CefBrowser> list = new LinkedList<>();
      if (getCefApp() != null && clientsField.isAvailable() && browsersField.isAvailable()) {
        Set<CefClient> clients = clientsField.get(ourCefApp);
        if (clients != null) {
          for (CefClient client : clients) {
            HashMap<?, CefBrowser> browsers = browsersField.get(client);
            if (browsers != null) {
              list.addAll(browsers.values());
            }
          }
        }
      }
      return list;
    }
  }

  public HwFacadeHelper(JComponent target) {
    myTarget = target;
  }

  private boolean isActive() {
    return myHwFacade != null;
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
          activateIfNeeded();
        }
      }
      @Override
      public void componentMoved(ComponentEvent e) {
        if (isActive()) {
          if (myHwFacade.isVisible()) myHwFacade.setLocation(myTarget.getLocationOnScreen());
        }
        else {
          activateIfNeeded();
        }
      }
    });

    activateIfNeeded();
  }

  private void activateIfNeeded() {
    if (SystemInfo.isLinux) return;
    if (!isCefAppActive()) return;

    Rectangle targetBounds = new Rectangle(myTarget.getLocationOnScreen(), myTarget.getSize());
    boolean overlaps = false;
    for (CefBrowser browser : JCEFAccessor.getBrowsers()) {
      Component comp = browser.getUIComponent();
      if (comp != null && comp.isVisible() && comp.isShowing() &&
          new Rectangle(comp.getLocationOnScreen(), comp.getSize()).intersects(targetBounds))
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
      myHwFacade.setBackground(TRANSPARENT_COLOR);
      myHwFacade.setVisible(true);
    }
  }

  public void addNotify() {
    if (myTarget.isVisible()) {
      onShowing();
    }
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
  }

  public void hide() {
    if (isActive()) {
      myHwFacade.setVisible(false);
    }
  }

  public void paint(Graphics g, Consumer<Graphics> targetPaint) {
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
