// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.StubItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.peer.ComponentPeer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

interface GlobalMenuLib extends Library {
  void runDbusServer(JLogger jlogger);
  void stopDbusServer();
  void execOnMainLoop(JRunnable run);

  Pointer registerWindow(long windowXid, EventHandler handler);
  void releaseWindowOnMainLoop(Pointer wi);

  void clearRootMenu(Pointer wi);
  void clearMenu(Pointer dbmi);

  Pointer addRootMenu(Pointer wi, int uid, String label);
  Pointer addMenuItem(Pointer parent, int uid, String label, int type);
  Pointer addSeparator(Pointer wi, int uid);

  void setItemLabel(Pointer item, String label);
  void setItemEnabled(Pointer item, boolean isEnabled);
  void setItemIcon(Pointer item, byte[] iconBytesPng, int iconBytesCount);

  interface EventHandler extends Callback {
    void handleEvent(int uid, int eventType);
  }
  interface JLogger extends Callback {
    void log(int level, String msg);
  }
  interface JRunnable extends Callback {
    void run();
  }

  int LOG_LEVEL_ERROR = 10;
  int LOG_LEVEL_INFO = 5;

  int EVENT_OPENED = 0;
  int EVENT_CLOSED = 1;
  int EVENT_CLICKED = 2;

  int SIGNAL_ACTIVATED = 3;
  int SIGNAL_ABOUT_TO_SHOW = 4;
  int SIGNAL_SHOWN = 5;
  int SIGNAL_CHILD_ADDED = 6;

  int ITEM_SIMPLE = 0;
  int ITEM_SUBMENU = 1;
  int ITEM_CHECK = 2;
  int ITEM_RADIO = 3;
}

public class GlobalMenuLinux implements GlobalMenuLib.EventHandler, Disposable {
  private static final Logger LOG = Logger.getInstance(GlobalMenuLinux.class);
  private static final GlobalMenuLib ourLib;
  private static final GlobalMenuLib.JLogger ourGLogger;
  private static final Thread ourGlibMainLoopThread;

  private final long myXid;
  private List<MenuItemInternal> myRoots;
  private Pointer myWindowHandle;
  private GlobalMenuLib.JRunnable myGlibLoopRunnable; // only to hold runnable object until it executed

  static {
    ourLib = _loadLibrary();
    if (ourLib != null) {
      ourGLogger = (level, msg) -> {
        if (level == GlobalMenuLib.LOG_LEVEL_INFO) {
          // System.out.println("INFO: " + msg);
          LOG.info(msg);
        } else {
          // System.out.println("ERROR: " + msg);
          LOG.error(msg);
        }
      };
      ourGlibMainLoopThread = new Thread(()->ourLib.runDbusServer(ourGLogger), "Glib-main-loop");
      ourGlibMainLoopThread.start();
    } else {
      ourGLogger = null;
      ourGlibMainLoopThread = null;
    }
  }

  public static GlobalMenuLinux create(@NotNull JFrame frame) {
    final long xid = _getX11WindowXid(frame);
    return xid == 0 ? null : new GlobalMenuLinux(xid);
  }

  private GlobalMenuLinux(long xid) {
    LOG.info("created instance of GlobalMenuLinux for xid=0x" + Long.toHexString(xid));
    myXid = xid;
  }

  @Override
  public void dispose() {
    if (ourLib == null)
      return;

    if (myWindowHandle != null)
      ourLib.releaseWindowOnMainLoop(myWindowHandle);
  }

  public void setRoots(List<ActionMenu> roots) {
    if (ourLib == null)
      return;

    ApplicationManager.getApplication().assertIsDispatchThread();

    final List<MenuItemInternal> newRoots = roots != null && !roots.isEmpty() ? new ArrayList<>(roots.size()) : null;
    if (newRoots != null) {
      for (ActionMenu am: roots) {
        // final int uid = myUid2MI.size();
        final int uid = System.identityHashCode(am);
        final MenuItemInternal mi = new MenuItemInternal(uid, GlobalMenuLib.ITEM_SUBMENU, am.getText(), null, true, am);
        //myUid2MI.put(uid, mi);
        newRoots.add(mi);
      }
    }

    myRoots = newRoots;

    myGlibLoopRunnable = () -> {
      // Glib-loop
      if (myWindowHandle == null) {
        myWindowHandle = ourLib.registerWindow(myXid, this);
        if (myWindowHandle == null) {
          LOG.error("AppMenu-service can't register xid " + myXid);
          return;
        }
      }

      ourLib.clearRootMenu(myWindowHandle);

      final List<MenuItemInternal> croots = myRoots;
      if (croots == null || croots.isEmpty())
        return;

      for (MenuItemInternal mi: croots)
        mi.nativePeer = ourLib.addRootMenu(myWindowHandle, mi.uid, mi.txt);
    };

    ourLib.execOnMainLoop(myGlibLoopRunnable); // TODO: clean ref myGlibLoopRunnable
  }

  private MenuItemInternal _findMenuItem(int uid) {
    return _findMenuItem(myRoots, uid);
  }

  private MenuItemInternal _findMenuItem(List<MenuItemInternal> kids, int uid) {
    if (kids == null || kids.isEmpty())
      return null;

    for (MenuItemInternal mi: kids) {
      if (mi.uid == uid)
        return mi;
      final MenuItemInternal child2 = _findMenuItem(mi.children, uid);
      if (child2 != null)
        return child2;
    }
    return null;
  }

  private static String _evtype2str(int eventType) {
    switch (eventType) {
      case GlobalMenuLib.EVENT_OPENED:
        return "event-opened";
      case GlobalMenuLib.EVENT_CLOSED:
        return "event-closed";
      case GlobalMenuLib.EVENT_CLICKED:
        return "event-clicked";
      case GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW:
        return "signal-about-to-show";
      case GlobalMenuLib.SIGNAL_ACTIVATED:
        return "signal-activated";
      case GlobalMenuLib.SIGNAL_CHILD_ADDED:
        return "signal-child-added";
      case GlobalMenuLib.SIGNAL_SHOWN:
        return "signal-shown";
    }
    return "unknown-event-type-"+eventType;
  }

  private static byte[] _icon2png(Icon icon) {
    if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
      return null;

    final BufferedImage img = UIUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g2d = img.createGraphics();
    icon.paintIcon(null, g2d, 0, 0);
    g2d.dispose();

    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      ImageIO.write(img, "png", bos);
      return bos.toByteArray();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private static MenuItemInternal _component2mi(Component each) {
    if (each == null)
      return null;

    if (each instanceof ActionMenuItem) {
      final ActionMenuItem ami = (ActionMenuItem)each;
      return new MenuItemInternal(System.identityHashCode(ami), ami.isToggleable() ? GlobalMenuLib.ITEM_CHECK : GlobalMenuLib.ITEM_SIMPLE, ami.getText(), _icon2png(ami.getIcon()), ami.isEnabled(), ami);
    }
    if (each instanceof ActionMenu) {
      final ActionMenu am2 = (ActionMenu)each;
      return new MenuItemInternal(System.identityHashCode(am2), GlobalMenuLib.ITEM_SUBMENU, am2.getText(), null, am2.isEnabled(), am2);
    }
    if (each instanceof JSeparator) {
      return new MenuItemInternal(System.identityHashCode(each), GlobalMenuLib.ITEM_SIMPLE, null, null, true, null);
    }
    if (each instanceof StubItem) {
      // System.out.println("skip separator");
    } else {
      LOG.error("unknown type of menu-item, class: " + each.getClass());
    }
    return null;
  }

  @Override
  public void handleEvent(int uid, int eventType) {
    // glib main-loop thread
    final MenuItemInternal mi = _findMenuItem(uid);
    if (mi == null) {
      LOG.error("can't find menu-item by uid " + uid + ", eventType=" + eventType);
      return;
    }
    if (mi.nativePeer == null) {
      LOG.error("menu-item hasn't native peer, uid = " + uid + ", eventType=" + eventType);
      return;
    }
    if (mi.jmenuitem == null) {
      LOG.error("menu-item hasn't associated swing peer, uid = " + uid + ", eventType=" + eventType);
      return;
    }

    if (eventType == GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW || eventType == GlobalMenuLib.EVENT_CLOSED) {
      if (!(mi.jmenuitem instanceof ActionMenu)) {
        LOG.error("about-to-show is emitted for non-ActionMenu item: " + mi.jmenuitem.getClass().getName());
        return;
      }

      final ActionMenu am = (ActionMenu)mi.jmenuitem;
      // System.out.printf("handle event %s from %s\n", _evtype2str(eventType), mi.toString());

      if (eventType == GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW) {
        // glib main-loop thread
        final List<MenuItemInternal> children = new ArrayList<>();

        final long startMs = System.currentTimeMillis();
        ApplicationManager.getApplication().invokeAndWait(()-> {
          // ETD-start
          am.removeAll();
          am.fillMenu();
          // System.out.println("\t size of components : " + am.getPopupMenu().getComponents().length);

          // collect children
          for (Component each : ((ActionMenu)mi.jmenuitem).getPopupMenu().getComponents()) {
            final MenuItemInternal cmi = _component2mi(each);
            if (cmi != null)
              children.add(cmi);
          }
        });
        final long elapsedMs = System.currentTimeMillis() - startMs;
        if (elapsedMs > 1000)
          LOG.info("global menu filled with " + children.size() + " components, spent " + elapsedMs + " ms");

        // return to glib main-loop thread
        ourLib.clearMenu(mi.nativePeer); // just for extra insurance
        for (MenuItemInternal child: children) {
          if (child.jmenuitem == null) {
            child.nativePeer = ourLib.addSeparator(mi.nativePeer, child.uid);
            continue;
          }
          child.nativePeer = ourLib.addMenuItem(mi.nativePeer, child.uid, child.txt, child.type);
          if (!child.isEnabled)
            ourLib.setItemEnabled(child.nativePeer, false);
          if (child.iconPngBytes != null && child.iconPngBytes.length > 0)
            ourLib.setItemIcon(child.nativePeer, child.iconPngBytes, child.iconPngBytes.length);
        }
        mi.children = children;
      } else if (eventType == GlobalMenuLib.EVENT_CLOSED) {
        // final long startMs = System.currentTimeMillis();
        ApplicationManager.getApplication().invokeLater(()-> {
          // ETD-start
          am.clearItems();
        });
        // final long elapsedMs = System.currentTimeMillis() - startMs;
        // System.out.printf("\t cleared menu '%s', spent %d ms\n", mi.txt, elapsedMs);

        // return to glib main-loop thread
        ourLib.clearMenu(mi.nativePeer);
        ourLib.addSeparator(mi.nativePeer, Integer.MAX_VALUE); // to prevent glib-warnings (about empty submenus)
        mi.children = null;
      }

      return;
    }

    if (eventType == GlobalMenuLib.EVENT_CLICKED) {
      if (!(mi.jmenuitem instanceof ActionMenuItem)) {
        LOG.error("clicked event for non-ActionMenuItem item: " + mi.jmenuitem.getClass().getName());
        return;
      }

      final ActionMenuItem ami = (ActionMenuItem)mi.jmenuitem;
      // System.out.printf("handle click event %s from %s\n", _evtype2str(eventType), mi.toString());

      ApplicationManager.getApplication().invokeLater(()-> ami.doClick());
    }
  }

  public static boolean isAvailable() { return ourLib != null; }

  private static GlobalMenuLib _loadLibrary() {
    if (!SystemInfo.isLinux)
      return null;

    if (!"Unity".equals(System.getenv("XDG_CURRENT_DESKTOP"))) {
      LOG.info("skip loading of dbusmenu wrapper because not-unity desktop used");
      return null;
    }

    UrlClassLoader.loadPlatformLibrary("dbm");

    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    final Map<String, Object> options = new HashMap<>();
    try {
      return Native.loadLibrary("dbm", GlobalMenuLib.class, options);
    } catch (UnsatisfiedLinkError ule) {
      LOG.error(ule);
    } catch (RuntimeException e) {
      LOG.error(e);
    }
    return null;
  }

  private static class MenuItemInternal {
    final int uid;
    final int type;
    final String txt;
    final byte[] iconPngBytes;
    final JMenuItem jmenuitem;
    final boolean isEnabled;
    Pointer nativePeer;
    List<MenuItemInternal> children;

    MenuItemInternal(int uid, int type, String txt, byte[] iconPngBytes, boolean isEnabled, JMenuItem jmenuitem) {
      this.uid = uid;
      this.type = type;
      this.txt = txt;
      this.iconPngBytes = iconPngBytes;
      this.isEnabled = isEnabled;
      this.jmenuitem = jmenuitem;
    }

    @Override
    public String toString() { return String.format("'%s' (%d)",txt, uid); }
  }

  private static long _getX11WindowXid(@NotNull JFrame frame) {
    final ComponentPeer wndPeer = frame.getPeer();
    if (wndPeer == null) {
      // wait a little for X11-peer to be connected
      LOG.info("frame peer is null, wait for connection");
      return 0;
    }

    // sun.awt.X11.XBaseWindow isn't available at all jdks => use reflection
    if (!wndPeer.getClass().getName().equals("sun.awt.X11.XFramePeer")) {
      LOG.info("frame peer isn't instance of XBaseWindow, class of peer: " + wndPeer.getClass());
      return 0;
    }

    // System.out.println("Window id (from XBaseWindow): 0x" + Long.toHexString(((XBaseWindow)frame.getPeer()).getWindow()));

    Method method = null;
    try {
      method = wndPeer.getClass().getMethod("getWindow");
    } catch (SecurityException e) {
      LOG.error(e);
    } catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    if (method == null)
      return 0;

    try {
      return (long)method.invoke(wndPeer);
    } catch (IllegalArgumentException e) {
      LOG.error(e);
    } catch (IllegalAccessException e) {
      LOG.error(e);
    } catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return 0;
  }
}