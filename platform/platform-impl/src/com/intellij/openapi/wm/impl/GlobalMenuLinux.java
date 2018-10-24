// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.StubItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.peer.ComponentPeer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.javafx.application.PlatformImpl;

interface GlobalMenuLib extends Library {
  void startWatchDbus(JLogger jlogger, JRunnable onAppmenuServiceAppeared, JRunnable onAppmenuServiceVanished);
  void stopWatchDbus();

  void runMainLoop(JLogger jlogger, JRunnable onAppmenuServiceAppeared, JRunnable onAppmenuServiceVanished);

  void execOnMainLoop(JRunnable run);

  Pointer registerWindow(long windowXid, EventHandler handler);
  void releaseWindowOnMainLoop(Pointer wi);

  void bindNewWindow(Pointer wi, long windowXid); // can be called from EDT (invokes only g_dbus_proxy_call, stateless)
  void unbindWindow(Pointer wi, long windowXid);  // can be called from EDT (invokes only g_dbus_proxy_call, stateless)

  void clearRootMenu(Pointer wi);
  void clearMenu(Pointer dbmi);

  Pointer addRootMenu(Pointer wi, int uid, String label);
  Pointer addMenuItem(Pointer parent, int uid, String label, int type);
  Pointer addSeparator(Pointer wi, int uid);

  void removeMenuItem(Pointer parent, Pointer item);

  void setItemLabel(Pointer item, String label);
  void setItemEnabled(Pointer item, boolean isEnabled);
  void setItemIcon(Pointer item, byte[] iconBytesPng, int iconBytesCount);
  void setItemShortcut(Pointer item, int jmodifiers, int jkeycode);

  void toggleItemStateChecked(Pointer item, boolean isChecked);

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

  int JMOD_SHIFT = 1;
  int JMOD_CTRL  = 1 << 1;
  int JMOD_ALT   = 1 << 2;
  int JMOD_META  = 1 << 3;
}

public class GlobalMenuLinux implements GlobalMenuLib.EventHandler, Disposable {
  private static final SimpleDateFormat ourDtf = new SimpleDateFormat("hhmmss.SSS"); // for debug only
  private static final boolean TRACE_SYSOUT               = Registry.is("linux.native.menu.debug.trace.sysout", false);
  private static final boolean TRACE_DISABLED             = Registry.is("linux.native.menu.debug.trace.disabled", true);
  private static final boolean TRACE_SYNC_STATS           = Registry.is("linux.native.menu.debug.trace.sync-stats", false);
  private static final boolean TRACE_EVENTS               = Registry.is("linux.native.menu.debug.trace.events", false);
  private static final boolean TRACE_EVENT_FILTER         = Registry.is("linux.native.menu.debug.trace.event-filter", false);
  private static final boolean TRACE_CLEARING             = Registry.is("linux.native.menu.debug.trace.clearing", false);
  private static final boolean TRACE_HIERARCHY_MISMATCHES = Registry.is("linux.native.menu.debug.trace.hierarchy.mismatches", false);
  private static final boolean SHOW_SWING_MENU            = Registry.is("linux.native.menu.debug.show.frame.menu", false);

  private static final Logger LOG = Logger.getInstance(GlobalMenuLinux.class);
  private static final GlobalMenuLib ourLib;
  private static final GlobalMenuLib.JLogger ourGLogger;
  private static final GlobalMenuLib.JRunnable ourProcessQueue;
  private static final GlobalMenuLib.JRunnable ourOnAppmenuServiceAppeared;
  private static final GlobalMenuLib.JRunnable ourOnAppmenuServiceVanished;
  private static final Map<Long, GlobalMenuLinux> ourInstances = new ConcurrentHashMap<>();
  private static boolean ourIsServiceAvailable = false;

  private final long myXid;
  private final @NotNull JFrame myFrame;
  private List<MenuItemInternal> myRoots;
  private Pointer myWindowHandle;
  private boolean myIsProcessed = false;

  private final EventFilter myEventFilter = new EventFilter();

  static {
    ourLib = _loadLibrary();
    if (ourLib != null) {
      ourGLogger = (level, msg) -> {
        if (level == GlobalMenuLib.LOG_LEVEL_INFO) {
          if (TRACE_SYSOUT)
            _trace(msg);
          else
            LOG.info(msg);
        } else {
          // System.out.println("ERROR: " + msg);
          LOG.error(msg);
        }
      };
      ourProcessQueue = () -> {
        // exec at glib-thread
        if (!ourIsServiceAvailable)
          return;

        for (GlobalMenuLinux gml: ourInstances.values())
          gml._processRoots();
      };
      ourOnAppmenuServiceAppeared = () -> {
        // exec at glib-thread
        LOG.info("Appeared dbus-service 'com.canonical.AppMenu.Registrar'");
        ourIsServiceAvailable = true;
        ourProcessQueue.run();
      };
      ourOnAppmenuServiceVanished = () -> {
        // exec at glib-thread
        LOG.info("Closed dbus-service 'com.canonical.AppMenu.Registrar'");
        ourIsServiceAvailable = false;
        for (GlobalMenuLinux gml: ourInstances.values()) {
          gml.myWindowHandle = null;
          ApplicationManager.getApplication().invokeLater(()->gml.myFrame.getJMenuBar().setVisible(true));
        }
      };

      // NOTE: linux implementation of javaFX starts native main loop with GtkApplication._runLoop()
      try {
        PlatformImpl.startup(() -> {
          ourLib.startWatchDbus(ourGLogger, ourOnAppmenuServiceAppeared, ourOnAppmenuServiceVanished);
        });
      } catch (Throwable e) {
        LOG.info("can't start main loop via javaFX (will run it manualy): " + e.getMessage());
        final Thread glibMain = new Thread(()->ourLib.runMainLoop(ourGLogger, ourOnAppmenuServiceAppeared, ourOnAppmenuServiceVanished));
        glibMain.start();
      }
    } else {
      ourGLogger = null;
      ourProcessQueue = null;
      ourOnAppmenuServiceAppeared = null;
      ourOnAppmenuServiceVanished = null;
    }
  }

  public static GlobalMenuLinux create(@NotNull JFrame frame) {
    final long xid = _getX11WindowXid(frame);
    return xid == 0 ? null : new GlobalMenuLinux(xid, frame);
  }

  private GlobalMenuLinux(long xid, @NotNull JFrame frame) {
    LOG.info("created instance of GlobalMenuLinux for xid=0x" + Long.toHexString(xid));
    myXid = xid;
    myFrame = frame;
    ourInstances.put(myXid, this);
  }

  @Override
  public void dispose() {
    if (ourLib == null)
      return;

    if (myWindowHandle != null) {
      LOG.info("scheduled destroying of GlobalMenuLinux for xid=0x" + Long.toHexString(myXid));
      ourLib.releaseWindowOnMainLoop(myWindowHandle);
    }
  }

  public void bindNewWindow(@NotNull Window frame) {
    // exec at EDT
    final long xid = _getX11WindowXid(frame);
    if (xid == 0) {
      LOG.warn("can't obtain XID of window: " + frame + ", skip global menu binding");
      return;
    }
    if (myWindowHandle != null) {
      _trace("bind new window 0x%X", xid);
      ourLib.bindNewWindow(myWindowHandle, xid);
      if (frame instanceof JFrame) {
        final JFrame jfr = (JFrame)frame;
        if (jfr.getJMenuBar() != null)
          jfr.getJMenuBar().setVisible(false);
      }
    }
  }

  public void unbindWindow(@NotNull Window frame) {
    // exec at EDT
    final long xid = _getX11WindowXid(frame);
    if (xid == 0) {
      LOG.warn("can't obtain XID of window: " + frame + ", skip global menu unbinding");
      return;
    }
    if (myWindowHandle != null) {
      _trace("unbind window 0x%X", xid);
      ourLib.unbindWindow(myWindowHandle, xid);
    }
  }

  public void setRoots(List<ActionMenu> roots) {
    if (ourLib == null)
      return;

    ApplicationManager.getApplication().assertIsDispatchThread();

    final int size = roots == null ? 0 : roots.size();
    final List<MenuItemInternal> newRoots = new ArrayList<>(size);
    if (roots != null) {
      for (ActionMenu am: roots) {
        final int uid = System.identityHashCode(am);
        final MenuItemInternal mi = new MenuItemInternal(newRoots.size(), uid, GlobalMenuLib.ITEM_SUBMENU, am.getAnAction());
        mi.jitem = am;
        mi.setLabelFromSwingPeer(am);
        newRoots.add(mi);
      }
    }

    myRoots = newRoots;
    _trace("set new menu roots, count=%d", size);
    myIsProcessed = false;
    ourLib.execOnMainLoop(ourProcessQueue);
  }

  private void _processRoots() {
    // exec at glib-thread
    if (myIsProcessed)
      return;

    myIsProcessed = true;

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

    if (!SHOW_SWING_MENU)
      ApplicationManager.getApplication().invokeLater(()->myFrame.getJMenuBar().setVisible(false));
  }

  private MenuItemInternal _findMenuItem(int uid) {
    return _findMenuItem(myRoots, uid);
  }

  private static MenuItemInternal _findMenuItem(List<MenuItemInternal> kids, int uid) {
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

  private static MenuItemInternal _createInternalFromSwing(Component each) {
    if (each == null)
      return null;
    MenuItemInternal result = null;
    if (each instanceof ActionMenuItem) {
      final ActionMenuItem ami = (ActionMenuItem)each;
      result = new MenuItemInternal(-1, System.identityHashCode(ami), ami.isToggleable() ? GlobalMenuLib.ITEM_CHECK : GlobalMenuLib.ITEM_SIMPLE, ami.getAnAction());
      result.jitem = ami;
    } else if (each instanceof ActionMenu) {
      final ActionMenu am2 = (ActionMenu)each;
      result = new MenuItemInternal(-1, System.identityHashCode(am2), GlobalMenuLib.ITEM_SUBMENU, am2.getAnAction());
      result.jitem = am2;
    } else if (each instanceof JSeparator) {
      result = new MenuItemInternal(-1, System.identityHashCode(each), GlobalMenuLib.ITEM_SIMPLE, null);
    } else if (each instanceof StubItem) {
      // System.out.println("skip StubItem");
    } else {
      LOG.error("unknown type of menu-item, class: " + each.getClass());
    }
    return result;
  }

  private static final int STAT_CREATED = 0;
  private static final int STAT_DELETED = 1;
  private static final int STAT_UPDATED = 2;

  private static String _stats2str(int[] stats) {
    if (stats == null)
      return "empty";
    return String.format("created=%d, deleted=%d, updated=%d", stats[STAT_CREATED], stats[STAT_DELETED], stats[STAT_UPDATED]);
  }

  private static void _syncChildren(@NotNull MenuItemInternal mi, @NotNull ActionMenu am, int deepness, int[] stats) {
    // exec at EDT

    // 1. mark all kids to delete
    mi.clearChildrenSwingRefs();
    for (MenuItemInternal cmi: mi.children)
      cmi.toDelete = true;
    if (stats != null) stats[STAT_DELETED] += mi.children.size();

    // 2. check all children from ActionMenu
    for (Component each : am.getPopupMenu().getComponents()) {
      MenuItemInternal cmi = mi.findCorrespondingChild(each);
      if (cmi == null) {
        cmi = _createInternalFromSwing(each);
        if (cmi != null) {
          mi.children.add(cmi);
          if (stats != null) ++stats[STAT_CREATED];
          if (each instanceof JMenuItem)
            cmi.updateBySwingPeer((JMenuItem)each);
        }
      } else {
        cmi.toDelete = false;
        if (stats != null) --stats[STAT_DELETED];
        if (each instanceof JMenuItem) {
          final boolean changed = cmi.updateBySwingPeer((JMenuItem)each);
          if (stats != null && changed) ++stats[STAT_UPDATED];
        }
      }
      if (cmi != null) {
        if (deepness > 1 && (each instanceof ActionMenu))
          _syncChildren(cmi, (ActionMenu)each, deepness - 1, stats);
      }
    }
  }

  private static void _processChildren(@NotNull MenuItemInternal mi) {
    // exec at glib main-loop thread
    if (mi.nativePeer == null)
      return;

    for (MenuItemInternal child: mi.children) {
      if (child.nativePeer != null) {
        if (child.toDelete) {
          ourLib.removeMenuItem(mi.nativePeer, child.nativePeer);
          child.nativePeer = null;
        } else {
          child.updateNative();
        }
      } else {
        if (child.action == null) {
          child.nativePeer = ourLib.addSeparator(mi.nativePeer, child.uid);
          continue;
        }

        child.nativePeer = ourLib.addMenuItem(mi.nativePeer, child.uid, child.txt, child.type);
        child.updateNative();
      }

      _processChildren(child);
    }
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
    if (mi.action == null) {
      LOG.error("menu-item hasn't associated AnAction, uid = " + uid + ", eventType=" + eventType);
      return;
    }

    if (TRACE_EVENTS) _trace("received event '%s' from item %s", _evtype2str(eventType), mi);

    if (eventType == GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW || eventType == GlobalMenuLib.EVENT_CLOSED) {
      final boolean check = myEventFilter.check(uid, eventType, mi);
      if (!check)
        return;

      if (eventType == GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW) {
        // glib main-loop thread
        final long startMs = System.currentTimeMillis();
        int[] stats = new int[]{0, 0, 0};

        ApplicationManager.getApplication().invokeAndWait(()-> {
          // ETD-start
          final JMenuItem jmi = mi.jitem;
          if (jmi == null) {
            if (TRACE_HIERARCHY_MISMATCHES) _trace("corresponding (opening) swing item is null, event source: " + mi + ", swing menu hierarchy:\n" + _dumpSwingHierarchy());
            return;
          }
          if (!(jmi instanceof ActionMenu)) {
            LOG.error("corresponding (opening) swing item isn't instance of ActionMenu, class=" + jmi.getClass().getName() + ", event source: " + mi);
            return;
          }

          final ActionMenu am = (ActionMenu)jmi;
          am.removeAll();
          am.fillMenu();
          _syncChildren(mi, am, 1, stats);
        });

        // glib main-loop thread
        final long elapsedMs = System.currentTimeMillis() - startMs;
        if (TRACE_SYNC_STATS) _trace("opened %s '%s', spent (in EDT) %d ms, stats: %s", (mi.isRoot() ? "root menu" : "submenu"), String.valueOf(mi.txt), elapsedMs, _stats2str(stats));

        _processChildren(mi);
      } else if (eventType == GlobalMenuLib.EVENT_CLOSED) {
        // glib main-loop thread
        mi.scheduleClearSwing();
      }

      return;
    }

    if (eventType == GlobalMenuLib.EVENT_CLICKED) {
      _trace("process click event (%s), event source: %s", _evtype2str(eventType), mi);

      final JMenuItem jmi = mi.jitem;
      if (jmi == null) {
        LOG.error("can't find corresponding (clicked) ActionMenuItem, event source: " + mi + ", swing menu hierarchy:\n" + _dumpSwingHierarchy());
        return;
      }
      if (!(jmi instanceof ActionMenuItem)) {
        LOG.error("corresponding (clicked) swing item isn't instance of ActionMenuItem, class=" + jmi.getClass().getName() + ", event source: " + mi);
        return;
      }

      final ActionMenuItem ami = (ActionMenuItem)jmi;
      ApplicationManager.getApplication().invokeLater(()-> ami.doClick());
    }
  }

  public static boolean isAvailable() { return ourLib != null; }

  private static GlobalMenuLib _loadLibrary() {
    if (!SystemInfo.isLinux || !Registry.is("linux.native.menu"))
      return null;

    try {
      UrlClassLoader.loadPlatformLibrary("dbm");

      // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
      // the way we tell CF to interpret our char*
      // May be removed if we use toStringViaUTF16
      System.setProperty("jna.encoding", "UTF8");

      final Map<String, Object> options = new HashMap<>();
      return Native.loadLibrary("dbm", GlobalMenuLib.class, options);
    } catch (UnsatisfiedLinkError ule) {
      LOG.info("disable global-menu integration because some of shared libraries isn't installed: " + ule);
    } catch (Throwable e) {
      LOG.error(e);
    }
    return null;
  }

  private static class MenuItemInternal {
    final int rootPos;
    final int uid;
    final int type;
    final AnAction action;

    final List<MenuItemInternal> children = new ArrayList<>();

    String txt;
    String originTxt;
    boolean isEnabled = true;
    boolean isChecked = false;
    byte[] iconPngBytes;

    JMenuItem jitem;
    Pointer nativePeer;
    boolean toDelete = false;

    long lastClosedMs = 0;

    Timer timerClearSwing;

    MenuItemInternal(int rootPos, int uid, int type, AnAction action) {
      this.rootPos = rootPos;
      this.uid = uid;
      this.type = type;
      this.action = action;
    }

    boolean isRoot() { return rootPos >= 0; }
    boolean isToggleable() { return type == GlobalMenuLib.ITEM_CHECK || type == GlobalMenuLib.ITEM_RADIO; }

    void clearChildrenSwingRefs() {
      for (MenuItemInternal cmi: children) {
        cmi.jitem = null;
        cmi.clearChildrenSwingRefs();
      }
    }

    // returns true when changed
    boolean updateBySwingPeer(@NotNull JMenuItem peer) {
      // exec at EDT
      jitem = peer;
      // NOTE: probably is's better to use sync flags like: if (cmi.isEnabled != each.isEnabled()) cmi.needUpdate = true;
      boolean res = isEnabled != peer.isEnabled();
      isEnabled = peer.isEnabled();
      if (isToggleable()) {
        if (isChecked != peer.isSelected()) res = true;
        isChecked = peer.isSelected();
      }
      if (!Objects.equals(originTxt, peer.getText())) {
        // _trace("label changes: '%s' -> '%s'", originTxt, peer.getText());
        setLabelFromSwingPeer(peer);
        res = true;
      }
      iconPngBytes = isToggleable() ? null : _icon2png(peer.getIcon());
      return res;
    }

    void setLabelFromSwingPeer(@NotNull JMenuItem peer) {
      // exec at EDT
      originTxt = peer.getText();
      txt = _buildMnemonicLabel(peer);
    }

    void updateNative() {
      // exec at glib-loop thread
      // NOTE: probably it's better to use sync flags, to avoid frequent calls, to avoid applet destabilization)
      if (nativePeer == null)
        return;

      ourLib.setItemLabel(nativePeer, txt);
      ourLib.setItemEnabled(nativePeer, isEnabled);
      ourLib.setItemIcon(nativePeer, iconPngBytes, iconPngBytes != null ? iconPngBytes.length : 0);
      if (isToggleable())
        ourLib.toggleItemStateChecked(nativePeer, isChecked);
    }

    MenuItemInternal findCorrespondingChild(@NotNull Component target) {
      if (target == null)
        return null;
      if (children.isEmpty())
        return null;

      if (target instanceof JSeparator) {
        for (MenuItemInternal child : children)
          if (child.toDelete && child.action == null)
            return child;
        return null;
      }

      if (!(target instanceof JMenuItem))
        return null;

      final JMenuItem jmi = (JMenuItem)target;

      // find by text
      final String label = jmi.getText();
      if (label != null && !label.isEmpty()) {
        for (MenuItemInternal child : children)
          if (label.equals(child.originTxt))
            return child;
      }

      // find by Action
      AnAction act = null;
      if (target instanceof ActionMenuItem)
        act = ((ActionMenuItem)target).getAnAction();
      if (target instanceof ActionMenu)
        act = ((ActionMenu)target).getAnAction();

      if (act == null)
        return null;

      for (MenuItemInternal child : children)
        if (act.equals(child.action)) {
          // System.out.println("WARN: can't find child of " + toString() + " corresponding by label '" + String.valueOf(label) + "' (will search by action), all children:\n" + printKids());
          return child;
        }

      return null;
    }

    String printKids() {
      final StringBuilder res = new StringBuilder();
      printKids(res, 0);
      return res.toString();
    }

    void printKids(StringBuilder out, int indent) {
      for (MenuItemInternal kid: children) {
        if (out.length() > 0)
          out.append('\n');
        for (int c = 0; c < indent; ++c) out.append('\t');
        out.append(kid.toString());
      }
    }

    String printHierarchy() {
      final StringBuilder res = new StringBuilder();
      printHierarchy(res, 0);
      return res.toString();
    }

    void printHierarchy(StringBuilder out, int indent) {
      for (MenuItemInternal kid: children) {
        if (out.length() > 0)
          out.append('\n');
        for (int c = 0; c < indent; ++c) out.append('\t');
        out.append(kid.toString());
        kid.printHierarchy(out, indent + 1);
      }
    }

    @Override
    public String toString() {
      String res = String.format("'%s' (uid=%d, act=%s)", txt, uid, String.valueOf(action));
      if (toDelete)
        res = res + " [toDelele]";
      return res;
    }

    String toStringShort() {
      String res = String.format("'%s'", txt);
      if (toDelete)
        res = res + " [D]";
      if (isRoot())
        res = "Root " + res;
      else
        res = "Submenu " + res;
      return res;
    }

    synchronized void scheduleClearSwing() {
      // exec at glib main-loop thread
      if (timerClearSwing != null)
        timerClearSwing.stop();

      timerClearSwing = new Timer(300, (e) -> {
        _clearSwing();
      });
      timerClearSwing.setRepeats(false);
      timerClearSwing.start();
      if (TRACE_CLEARING) _trace("\t scheduled (300 ms later) to clear '%s'", toStringShort());
    }

    synchronized void cancelClearSwing() {
      // exec at glib main-loop thread
      timerClearSwing = null;
    }

    private void _clearSwing() {
      // exec at ETD
      if (timerClearSwing == null)
        return;

      if (jitem == null) {
        if (TRACE_CLEARING) _trace("corresponding (closing) swing item is null - nothing to clear, event source: ", this);
        return;
      }
      if (!(jitem instanceof ActionMenu)) {
        LOG.error("corresponding (closing) swing item isn't instance of ActionMenu, class=" + jitem.getClass().getName() + ", event source: " + toString());
        return;
      }

      final ActionMenu am = (ActionMenu)jitem;
      am.clearItems();
      clearChildrenSwingRefs();
      if (TRACE_CLEARING) _trace("\t cleared '%s'", toStringShort());
    }
  }

  private class EventFilter {
    private Timer myTimer;
    private long myLastFirstRootEventMs = 0;
    private GlobalMenuLib.JRunnable myGlibLoopRunnable;

    boolean check(int uid, int eventType, @NotNull MenuItemInternal mi) {
      final long timeMs = System.currentTimeMillis();
      if (eventType == GlobalMenuLib.EVENT_CLOSED) {
        mi.lastClosedMs = timeMs;
      } else {
        if (mi.rootPos == 0) {
          if (myTimer == null) {
            myLastFirstRootEventMs = timeMs;
            // start timer to call handleEvent(uid, eventType) after several ms
            myTimer = new Timer(50, (e) -> {
              if (myTimer == null) {
                if (TRACE_EVENT_FILTER) _trace("EventFilter: skip delayed 'about-to-show' processing of first-root because timer was reset (i.e. myTimer == null)");
                return;
              }
              ourLib.execOnMainLoop(myGlibLoopRunnable = () -> handleEvent(uid, eventType));
            });
            myTimer.setRepeats(false);
            myTimer.start();
            if (TRACE_EVENT_FILTER) _trace("EventFilter: start timer to process 'about-to-show' of first-root later");
            return false;
          }

          myTimer = null;
        } else if (mi.rootPos > 0) {
          if ((timeMs - myLastFirstRootEventMs) < 50) {
            if (TRACE_EVENT_FILTER) _trace("EventFilter: skip fake 'about-to-show' of root[%d]%s", mi.rootPos, myTimer != null ? " (reset timer)" : "");
            if (myTimer != null) {
              myTimer.stop();
              myTimer = null;
            }
            return false;
          }
          if (TRACE_EVENT_FILTER) _trace("EventFilter: process real 'about-to-show' on root[%d]", mi.rootPos);
        } else {
          if (TRACE_EVENT_FILTER) _trace("EventFilter: process real 'about-to-show' on non-root item '%s'%s", mi.txt, myTimer != null ? " (reset timer)" : "");
          if (myTimer != null) {
            myTimer.stop();
            myTimer = null;
          }
        }
      }

      return true;
    }
  }

  private static int _calcModifiers(JMenuItem jmenuitem) {
    if (jmenuitem == null || jmenuitem.getAccelerator() == null)
      return 0;

    final int modifiers = jmenuitem.getAccelerator().getModifiers();
    int result = 0;
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0 ) result |= GlobalMenuLib.JMOD_SHIFT;
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0 ) result |= GlobalMenuLib.JMOD_CTRL;
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0 ) result |= GlobalMenuLib.JMOD_META;
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0 ) result |= GlobalMenuLib.JMOD_ALT;
    return result;
  }

  private static int _calcKeyCode(JMenuItem jmenuitem) {
    if (jmenuitem == null || jmenuitem.getAccelerator() == null)
      return 0;
    return jmenuitem.getAccelerator().getKeyCode();
  }

  private static String _buildMnemonicLabel(JMenuItem jmenuitem) {
    String text = jmenuitem.getText();
    final int mnemonicCode = jmenuitem.getMnemonic();
    final int mnemonicIndex = jmenuitem.getDisplayedMnemonicIndex();
    if (text == null)
      text = "";
    final int index;
    if (mnemonicIndex >= 0 && mnemonicIndex < text.length() && Character.toUpperCase(text.charAt(mnemonicIndex)) == mnemonicCode) {
      index = mnemonicIndex;
    } else {
      // Mnemonic mismatch index
      index = -1;
      // LOG.error("Mnemonic code " + mnemonicCode + " mismatch index " + mnemonicIndex + " with txt: " + text);
    }

    final StringBuilder res = new StringBuilder(text);
    if(index != -1)
      res.insert(index, '_');
    return res.toString();
  }

  @SuppressWarnings("deprecation")
  private static long _getX11WindowXid(@NotNull Window frame) {
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

  private String _dumpSwingHierarchy() {
    StringBuilder out = new StringBuilder();
    _dumpSwingHierarchy(out);
    return out.toString();
  }

  private void _dumpSwingHierarchy(StringBuilder out) {
    for (MenuItemInternal root: myRoots) {
      final ActionMenu am = (ActionMenu)root.jitem;
      out.append(am.getText());
      out.append('\n');
      _dumpActionMenuKids(am, out, 1);
    }
  }

  private static void _dumpActionMenuKids(@NotNull ActionMenu am, StringBuilder out, int indent) {
    // exec at EDT
    for (Component each : am.getPopupMenu().getComponents()) {
      if (each == null)
        continue;
      if (!(each instanceof JMenuItem))
        continue;

      for (int c = 0; c < indent; ++c) out.append('\t');
      String txt = each instanceof JSeparator ? "--separ--" : ((JMenuItem)each).getText();
      if (txt == null || txt.isEmpty())
        txt = "null";
      out.append(txt);
      out.append('\n');

      if (each instanceof ActionMenu) {
        _dumpActionMenuKids((ActionMenu)each, out, indent + 1);
      }
    }
  }

  private static void _trace(String fmt, Object... args) {
    if (TRACE_DISABLED)
      return;
    final String msg = String.format(fmt, args);
    _trace(msg);
  }
  private static void _trace(String msg) {
    if (TRACE_DISABLED)
      return;
    if (TRACE_SYSOUT)
      System.out.println(ourDtf.format(new Date()) + ": " + msg);
    else
      LOG.info(msg);
  }
}