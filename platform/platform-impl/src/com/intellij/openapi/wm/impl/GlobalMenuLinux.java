// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettings;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.StubItem;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.ImageUtil;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.peer.ComponentPeer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

interface GlobalMenuLib extends Library {
  void runMainLoop(JLogger jlogger, JRunnable onAppmenuServiceAppeared, JRunnable onAppmenuServiceVanished);

  void execOnMainLoop(JRunnable run);

  Pointer registerWindow(long windowXid, LinuxGlobalMenuEventHandler handler);

  void releaseWindowOnMainLoop(Pointer wi, JRunnable onReleased);

  void bindNewWindow(Pointer wi, long windowXid); // can be called from EDT (invokes only g_dbus_proxy_call, stateless)

  void unbindWindow(Pointer wi, long windowXid);  // can be called from EDT (invokes only g_dbus_proxy_call, stateless)

  void clearRootMenu(Pointer wi);

  void clearMenu(Pointer dbmi);

  Pointer addRootMenu(Pointer wi, int uid, String label);

  Pointer addMenuItem(Pointer parent, int uid, String label, int type, int position);

  Pointer addSeparator(Pointer wi, int uid, int position);

  void reorderMenuItem(Pointer parent, Pointer item, int position);

  void removeMenuItem(Pointer parent, Pointer item);

  void showMenuItem(Pointer item);

  void setItemLabel(Pointer item, String label);

  void setItemEnabled(Pointer item, boolean isEnabled);

  void setItemIcon(Pointer item, byte[] iconBytesPng, int iconBytesCount);

  void setItemShortcut(Pointer item, int jmodifiers, int x11keycode);

  void toggleItemStateChecked(Pointer item, boolean isChecked);

  interface JLogger extends Callback {
    void log(int level, String msg);
  }

  interface JRunnable extends Callback {
    void run();
  }

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

public final class GlobalMenuLinux implements LinuxGlobalMenuEventHandler, Disposable {
  private static final String TOGGLE_SWING_MENU_ACTION_ID = "ToggleGlobalLinuxMenu";

  private static final SimpleDateFormat ourDtf = new SimpleDateFormat("hhmmss.SSS"); // for debug only
  private static final boolean TRACE_SYSOUT = Boolean.getBoolean("linux.native.menu.debug.trace.sysout");
  private static final boolean TRACE_ENABLED = Boolean.getBoolean("linux.native.menu.debug.trace.enabled");
  private static final boolean TRACE_SYNC_STATS = Boolean.getBoolean("linux.native.menu.debug.trace.sync-stats");
  private static final boolean TRACE_EVENTS = Boolean.getBoolean("linux.native.menu.debug.trace.events");
  private static final boolean TRACE_EVENT_FILTER = Boolean.getBoolean("linux.native.menu.debug.trace.event-filter");
  private static final boolean TRACE_SKIPPED_EVENT = Boolean.getBoolean("linux.native.menu.debug.trace.skipped.event");
  private static final boolean TRACE_CLEARING = Boolean.getBoolean("linux.native.menu.debug.trace.clearing");
  private static final boolean TRACE_HIERARCHY_MISMATCHES = Boolean.getBoolean("linux.native.menu.debug.trace.hierarchy.mismatches");
  private static final boolean SHOW_SWING_MENU = Boolean.getBoolean("linux.native.menu.debug.show.frame.menu");

  private static final boolean KDE_DISABLE_ROOT_MNEMONIC_PROCESSING = Boolean.getBoolean("linux.native.menu.kde.disable.root.mnemonic");

  private static final boolean SKIP_OPEN_MENU_COMMAND = Boolean.getBoolean("linux.native.menu.skip.open");
  private static final boolean DO_FILL_ROOTS = Boolean.getBoolean("linux.native.do.fill.roots");
  private static final boolean DONT_FILL_SUBMENU = Boolean.getBoolean("linux.native.menu.dont.fill.submenu");
  private static final boolean DONT_CLOSE_POPUPS = Boolean.getBoolean("linux.native.menu.dont.close.popups");
  private static final boolean DISABLE_EVENTS_FILTERING = Boolean.getBoolean("linux.native.menu.disable.events.filtering");

  private static final Logger LOG = Logger.getInstance(GlobalMenuLinux.class);
  private static final GlobalMenuLib ourLib;
  private static final GlobalMenuLib.JLogger ourGLogger;
  private static final GlobalMenuLib.JRunnable ourUpdateAllRoots;
  private static final GlobalMenuLib.JRunnable ourOnAppmenuServiceAppeared;
  private static final GlobalMenuLib.JRunnable ourOnAppmenuServiceVanished;
  private static final Map<Long, GlobalMenuLinux> ourInstances = new ConcurrentHashMap<>();
  private static boolean ourIsServiceAvailable = false;

  private final long myXid;
  private final @NotNull JFrame myFrame;
  private List<MenuItemInternal> myRoots;
  private Pointer myWindowHandle;
  private boolean myIsRootsUpdated = false;
  private boolean myIsEnabled = true;
  private boolean myIsDisposed = false;
  private boolean myIsFirstFilling = true; // don't filter first packet of events (it causes slow reaction of KDE applet)

  private final GlobalMenuLib.JRunnable myOnWindowReleased;
  private final EventFilter myEventFilter = new EventFilter();

  static {
    ourLib = _loadLibrary();
    if (ourLib == null) {
      ourGLogger = null;
      ourUpdateAllRoots = null;
      ourOnAppmenuServiceAppeared = null;
      ourOnAppmenuServiceVanished = null;
    }
    else {
      ourGLogger = (level, msg) -> {
        if (level == GlobalMenuLib.LOG_LEVEL_INFO) {
          if (TRACE_SYSOUT) {
            _trace(msg);
          }
          else {
            LOG.info(msg);
          }
        }
        else {
          // System.out.println("ERROR: " + msg);
          LOG.error(msg);
        }
      };
      ourUpdateAllRoots = () -> {
        // exec at glib-thread
        if (!ourIsServiceAvailable) {
          return;
        }

        for (GlobalMenuLinux gml : ourInstances.values()) {
          gml._updateRoots();
        }
      };
      ourOnAppmenuServiceAppeared = () -> {
        // exec at glib-thread
        LOG.info("Appeared dbus-service 'com.canonical.AppMenu.Registrar'");
        ourIsServiceAvailable = true;
        ourUpdateAllRoots.run();
      };
      ourOnAppmenuServiceVanished = () -> {
        // exec at glib-thread
        LOG.info("Closed dbus-service 'com.canonical.AppMenu.Registrar'");
        ourIsServiceAvailable = false;
        final boolean isMainMenuVisible = UISettings.getInstance().getShowMainMenu();
        for (GlobalMenuLinux gml : ourInstances.values()) {
          gml.myWindowHandle = null;
          if (isMainMenuVisible) {
            ApplicationManager.getApplication().invokeLater(() -> {
              final JMenuBar jmenubar = gml.myFrame.getJMenuBar();
              if (jmenubar != null) {
                jmenubar.setVisible(true);
              }
            });
          }
        }
      };

      final String threadName = "GlobalMenuLinux loop";
      new Thread(() -> ourLib.runMainLoop(ourGLogger, ourOnAppmenuServiceAppeared, ourOnAppmenuServiceVanished),
                                         threadName).start();
      LOG.info("Start glib main loop in thread: " + threadName);
    }
  }

  static final class MyActionTuner implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager actionManager) {
      if (!SystemInfo.isLinux || ApplicationManager.getApplication().isUnitTestMode() || !isPresented()) {
        return;
      }

      // register toggle-swing-menu action (to be able to enable swing menu when system applet is died)
      actionManager
        .registerAction(TOGGLE_SWING_MENU_ACTION_ID, new AnAction(IdeBundle.message("action.toggle.global.menu.integration.text"),
                                                                  IdeBundle.message("action.enable.disable.global.menu.integration.description"), null) {
          boolean enabled = false;

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            for (GlobalMenuLinux gml : ourInstances.values()) {
              gml.toggle(enabled);
            }
            enabled = !enabled;
          }
        });
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
    myOnWindowReleased = () -> {
      // exec at glib-thread
      myWindowHandle = null;
      if (myRoots != null) {
        for (MenuItemInternal root : myRoots) {
          root.nativePeer = null;
          root.children.clear();
        }
      }
      if (myIsDisposed) {
        ourInstances.remove(myXid);
      }
    };

    if (SystemInfo.isKDE && !KDE_DISABLE_ROOT_MNEMONIC_PROCESSING) {
      // root menu items doesn't catch mnemonic shortcuts (in KDE), so process them inside IDE
      IdeEventQueue.getInstance().addDispatcher(e -> {
        if (!(e instanceof KeyEvent)) {
          return false;
        }

        final KeyEvent event = (KeyEvent)e;
        if (!event.isAltDown()) {
          return false;
        }

        final Component src = event.getComponent();
        final Window wndParent = src instanceof Window ? (Window)src : SwingUtilities.windowForComponent(src);
        final char eventChar = Character.toUpperCase(event.getKeyChar());

        for (GlobalMenuLinux gml : ourInstances.values()) {
          if (gml.myFrame == wndParent) {
            List<MenuItemInternal> currentRoots = gml.myRoots;
            if (currentRoots == null) return false;

            for (MenuItemInternal root : currentRoots) {
              if (eventChar == root.mnemonic) {
                ourLib.showMenuItem(root.nativePeer);
                return false;
              }
            }
            return false;
          }
        }

        return false;
      }, this);
    }

    ourInstances.put(myXid, this);
  }

  @Override
  public void dispose() {
    // exec at EDT
    if (ourLib == null || myIsDisposed) {
      return;
    }

    myIsDisposed = true;

    if (myWindowHandle != null) {
      _trace("dispose frame, scheduled destroying of GlobalMenuLinux for xid=0x%X", myXid);
      ourLib.releaseWindowOnMainLoop(myWindowHandle, myOnWindowReleased);
    }
  }

  public void bindNewWindow(@NotNull Window frame) {
    // exec at EDT
    if (ourLib == null) {
      return;
    }

    final long xid = _getX11WindowXid(frame);
    if (xid == 0) {
      LOG.debug("can't obtain XID of window: " + frame + ", skip global menu binding");
      return;
    }
    if (myWindowHandle != null) {
      _trace("bind new window 0x%X", xid);
      ourLib.bindNewWindow(myWindowHandle, xid);
    }
  }

  public void unbindWindow(@NotNull Window frame) {
    // exec at EDT
    if (ourLib == null) {
      return;
    }

    final long xid = _getX11WindowXid(frame);
    if (xid == 0) {
      LOG.debug("can't obtain XID of window: " + frame + ", skip global menu unbinding");
      return;
    }
    if (myWindowHandle != null) {
      _trace("unbind window 0x%X", xid);
      ourLib.unbindWindow(myWindowHandle, xid);
    }
  }

  public void setRoots(List<ActionMenu> roots) {
    // exec at EDT
    if (ourLib == null) {
      return;
    }

    ApplicationManager.getApplication().assertIsDispatchThread();

    int[] stats = new int[]{0, 0, 0};
    final int size = roots == null ? 0 : roots.size();
    final List<MenuItemInternal> newRoots = new ArrayList<>(size);

    if (roots != null) {
      for (ActionMenu am : roots) {
        final int uid = System.identityHashCode(am);
        final MenuItemInternal mi = new MenuItemInternal(null, newRoots.size(), uid, GlobalMenuLib.ITEM_SUBMENU, am.getAnAction());
        mi.jitem = am;
        mi.setLabelFromSwingPeer(am);
        newRoots.add(mi);

        if (DO_FILL_ROOTS) {
          final long startMs = System.currentTimeMillis();
          am.removeAll(); // just for insurance
          am.fillMenu();
          _syncChildren(mi, am, 1, stats); // NOTE: fill root menus to avoid empty submenu showing
          am.removeAll();
          final long elapsedMs = System.currentTimeMillis() - startMs;
          if (TRACE_SYNC_STATS) {
            _trace("filled root menu '%s', spent (in EDT) %d ms, stats: %s", String.valueOf(mi.txt), elapsedMs, _stats2str(stats));
          }
        }
      }
    }

    myRoots = newRoots;
    _trace("set new menu roots, count=%d", size);
    myIsRootsUpdated = false;
    ourLib.execOnMainLoop(ourUpdateAllRoots);
  }

  private void _updateRoots() {
    // exec at glib-thread
    if (myIsRootsUpdated || !myIsEnabled || myIsDisposed) {
      return;
    }

    myIsRootsUpdated = true;

    if (myWindowHandle == null) {
      myWindowHandle = ourLib.registerWindow(myXid, this);
      if (myWindowHandle == null) {
        LOG.error("AppMenu-service can't register xid " + myXid);
        return;
      }
    }

    ourLib.clearRootMenu(myWindowHandle);

    final List<MenuItemInternal> croots = myRoots;
    if (croots == null || croots.isEmpty()) {
      return;
    }

    for (MenuItemInternal mi : croots) {
      mi.nativePeer = ourLib.addRootMenu(myWindowHandle, mi.uid, mi.txt);
      _processChildren(mi);
    }

    if (!SHOW_SWING_MENU) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myIsEnabled) {
          myFrame.getJMenuBar().setVisible(false);
        }
      });
    }
  }

  public void toggle(boolean enabled) {
    if (ourLib == null || myIsDisposed) {
      return;
    }

    if (myIsEnabled == enabled) {
      return;
    }

    myIsEnabled = enabled;

    if (enabled) {
      _trace("enable global-menu");
      myIsRootsUpdated = false;
      ourLib.execOnMainLoop(ourUpdateAllRoots);
    }
    else {
      if (myWindowHandle != null) {
        _trace("disable global menu, scheduled destroying of GlobalMenuLinux for xid=0x%X", myXid);
        ourLib.releaseWindowOnMainLoop(myWindowHandle, myOnWindowReleased);
      }

      if (UISettings.getInstance().getShowMainMenu()) {
        final JMenuBar frameMenu = myFrame.getJMenuBar();
        if (frameMenu != null) {
          frameMenu.setVisible(true);
        }
      }
    }
  }

  private MenuItemInternal _findMenuItem(int uid) {
    return _findMenuItem(myRoots, uid);
  }

  private static MenuItemInternal _findMenuItem(List<? extends MenuItemInternal> kids, int uid) {
    if (kids == null || kids.isEmpty()) {
      return null;
    }

    for (MenuItemInternal mi : kids) {
      if (mi.uid == uid) {
        return mi;
      }
      final MenuItemInternal child2 = _findMenuItem(mi.children, uid);
      if (child2 != null) {
        return child2;
      }
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
    return "unknown-event-type-" + eventType;
  }

  private static byte[] _icon2png(Icon icon) {
    if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
      return null;
    }

    final BufferedImage img = ImageUtil.createImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
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

  private static MenuItemInternal _createInternalFromSwing(MenuItemInternal parent, Component each) {
    if (each == null) {
      return null;
    }
    MenuItemInternal result = null;
    if (each instanceof ActionMenuItem) {
      final ActionMenuItem ami = (ActionMenuItem)each;
      result = new MenuItemInternal(parent, -1, System.identityHashCode(ami),
                                    ami.isToggleable() ? GlobalMenuLib.ITEM_CHECK : GlobalMenuLib.ITEM_SIMPLE, ami.getAnAction());
      result.jitem = ami;
    }
    else if (each instanceof ActionMenu) {
      final ActionMenu am2 = (ActionMenu)each;
      result = new MenuItemInternal(parent, -1, System.identityHashCode(am2), GlobalMenuLib.ITEM_SUBMENU, am2.getAnAction());
      result.jitem = am2;
    }
    else if (each instanceof JSeparator) {
      result = new MenuItemInternal(parent, -1, System.identityHashCode(each), GlobalMenuLib.ITEM_SIMPLE, null);
    }
    else if (each instanceof StubItem) {
      // System.out.println("skip StubItem");
    }
    else {
      LOG.error("unknown type of menu-item, class: " + each.getClass());
    }
    return result;
  }

  private static final int STAT_CREATED = 0;
  private static final int STAT_DELETED = 1;
  private static final int STAT_UPDATED = 2;

  private static String _stats2str(int[] stats) {
    if (stats == null) {
      return "empty";
    }
    return String.format("created=%d, deleted=%d, updated=%d", stats[STAT_CREATED], stats[STAT_DELETED], stats[STAT_UPDATED]);
  }

  private static void _syncChildren(@NotNull MenuItemInternal mi, @NotNull ActionMenu am, int deepness, int[] stats) {
    // exec at EDT

    // 1. mark all kids to delete
    mi.clearChildrenSwingRefs();
    for (MenuItemInternal cmi : mi.children) {
      cmi.position = -1; // mark item to be deleted
    }
    if (stats != null) stats[STAT_DELETED] += mi.children.size();

    // 2. check all children from ActionMenu
    int itemPos = 0;
    for (Component each : am.getPopupMenu().getComponents()) {
      MenuItemInternal cmi = mi.findCorrespondingChild(each);
      if (cmi == null) {
        cmi = _createInternalFromSwing(mi, each);
        if (cmi != null) {
          cmi.position = itemPos++;
          mi.children.add(cmi);
          if (stats != null) ++stats[STAT_CREATED];
          if (each instanceof JMenuItem) {
            cmi.updateBySwingPeer((JMenuItem)each);
          }
        }
      }
      else {
        cmi.position = itemPos++;
        if (stats != null) --stats[STAT_DELETED];
        if (each instanceof JMenuItem) {
          final boolean changed = cmi.updateBySwingPeer((JMenuItem)each);
          if (stats != null && changed) ++stats[STAT_UPDATED];
        }
      }
      if (cmi != null) {
        if (deepness > 1 && (each instanceof ActionMenu)) {
          final ActionMenu jmiEach = (ActionMenu)each;
          jmiEach.removeAll();
          jmiEach.fillMenu();
          _syncChildren(cmi, jmiEach, deepness - 1, stats);
        }
      }
    }
  }

  private static void _processChildren(@NotNull MenuItemInternal mi) {
    // exec at glib main-loop thread
    if (mi.nativePeer == null) {
      return;
    }

    // sort
    mi.children.sort(Comparator.comparingInt(MenuItemInternal::getPosition));

    // remove marked items
    Iterator<MenuItemInternal> i = mi.children.iterator();
    while (i.hasNext()) {
      final MenuItemInternal child = i.next();
      if (child.position != -1) {
        break;
      }

      if (child.nativePeer != null) {
        ourLib.removeMenuItem(mi.nativePeer, child.nativePeer);
        child.nativePeer = null;
      }
      i.remove();
    }

    // update/create and reorder
    for (int pos = 0; pos < mi.children.size(); ++pos) {
      final MenuItemInternal child = mi.children.get(pos);

      if (child.nativePeer == null) {
        if (child.action == null) {
          child.nativePeer = ourLib.addSeparator(mi.nativePeer, child.uid, pos);
          continue;
        }

        child.nativePeer = ourLib.addMenuItem(mi.nativePeer, child.uid, child.txt, child.type, child.position);
      }
      else if (child.position != pos) {
        // System.out.printf("reorder: '%s' [%d] -> [%d]\n", child, child.position, pos);
        ourLib.reorderMenuItem(mi.nativePeer, child.nativePeer, child.position);
      }

      child.updateNative();
      _processChildren(child);
    }
  }

  private static boolean _isFillEvent(int eventType) {
    return eventType == GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW || (!SKIP_OPEN_MENU_COMMAND && eventType == GlobalMenuLib.EVENT_OPENED);
  }

  @Override
  public void handleEvent(int uid, int eventType) {
    _handleEvent(uid, eventType, true);
  }

  private void _handleEvent(int uid, int eventType, boolean doFiltering) {
    // glib main-loop thread
    if (myWindowHandle == null || myIsDisposed) {
      if (TRACE_ENABLED) _trace("window was closed when received event '%s', just skip it", _evtype2str(eventType));
      return;
    }

    final MenuItemInternal mi = _findMenuItem(uid);
    if (mi == null) {
      LOG.debug("can't find menu-item by uid " + uid + ", eventType=" + eventType);
      return;
    }
    if (mi.nativePeer == null) {
      LOG.debug("menu-item hasn't native peer, uid = " + uid + ", eventType=" + eventType);
      return;
    }
    if (mi.action == null) {
      LOG.debug("menu-item hasn't associated AnAction, uid = " + uid + ", eventType=" + eventType);
      return;
    }

    if (TRACE_EVENTS) _trace("received event '%s' from item %s", _evtype2str(eventType), mi);

    if (!DISABLE_EVENTS_FILTERING && !myIsFirstFilling && doFiltering && !myEventFilter.check(uid, eventType, mi)) {
      return;
    }

    if (myIsFirstFilling) {
      final Timer timer = new Timer(5000, e -> myIsFirstFilling = false);
      timer.setRepeats(false);
      timer.start();
    }

    if (_isFillEvent(eventType)) {
      // glib main-loop thread
      if (!DONT_CLOSE_POPUPS) {
        ApplicationManager.getApplication().invokeLater(() -> IdeEventQueue.getInstance().getPopupManager().closeAllPopups());
      }
      mi.cancelClearSwing();

      // simple check to avoid double (or frequent) filling
      final long timeMs = System.currentTimeMillis();
      int[] stats = new int[]{0, 0, 0};
      if (timeMs - mi.lastFilledMs < 1500 && mi.lastClearedMs < mi.lastFilledMs) {
        if (TRACE_SKIPPED_EVENT) _trace("skipped fill-event for item '%s', use cached (too frequent fill-events)", String.valueOf(mi.txt));
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          // ETD-start
          final JMenuItem jmi = mi.jitem;
          if (jmi == null) {
            if (TRACE_HIERARCHY_MISMATCHES) {
              _trace(
                "corresponding (opening) swing item is null, event source: " + mi + ", swing menu hierarchy:\n" + _dumpSwingHierarchy());
            }
            return;
          }
          if (!(jmi instanceof ActionMenu)) {
            LOG.debug("corresponding (opening) swing item isn't instance of ActionMenu, class=" +
                      jmi.getClass().getName() +
                      ", event source: " +
                      mi);
            return;
          }

          mi.lastFilledMs = timeMs;

          final ActionMenu am = (ActionMenu)jmi;
          am.removeAll();
          am.fillMenu();
          _syncChildren(mi, am, DONT_FILL_SUBMENU ? 1 : 2,
                        stats); // NOTE: fill next submenus level to avoid empty submenu showing (intermittent behaviour of menu-applet)
        });

        // glib main-loop thread
        final long elapsedMs = System.currentTimeMillis() - timeMs;
        if (TRACE_SYNC_STATS) {
          _trace("filled menu %s '%s', spent (in EDT) %d ms, stats: %s", (mi.isRoot() ? "root menu" : "submenu"), String.valueOf(mi.txt),
                 elapsedMs, _stats2str(stats));
        }

        _processChildren(mi);
      }
    }

    if (eventType == GlobalMenuLib.EVENT_CLOSED) {
      // glib main-loop thread
      mi.scheduleClearSwing();
      return;
    }

    if (eventType == GlobalMenuLib.EVENT_CLICKED) {
      _trace("process click event (%s), event source: %s", _evtype2str(eventType), mi);

      final JMenuItem jmi = mi.jitem;
      if (jmi == null) {
        if (TRACE_HIERARCHY_MISMATCHES) {
          _trace(
            "can't find corresponding (clicked) ActionMenuItem, event source: " + mi + ", swing menu hierarchy:\n" + _dumpSwingHierarchy());
        }
        return;
      }
      if (!(jmi instanceof ActionMenuItem)) {
        LOG.debug("corresponding (clicked) swing item isn't instance of ActionMenuItem, class=" +
                  jmi.getClass().getName() +
                  ", event source: " +
                  mi);
        return;
      }

      final ActionMenuItem ami = (ActionMenuItem)jmi;
      ApplicationManager.getApplication().invokeLater(() -> ami.doClick());
    }
  }

  // return true when native library was loaded (and dependent packages like glib-dbusmenu were installed)
  public static boolean isAvailable() { return ourLib != null; }

  // return true when corresponding dbus-service is alive
  public static boolean isPresented() {
    return ourLib != null && ourIsServiceAvailable;
  }

  private static GlobalMenuLib _loadLibrary() {
    Application app;
    if (!SystemInfo.isLinux ||
        (app = ApplicationManager.getApplication()) == null || app.isUnitTestMode() || app.isHeadlessEnvironment() ||
        Registry.is("linux.native.menu.force.disable") ||
        (LoadingState.COMPONENTS_REGISTERED.isOccurred() && !Experiments.getInstance().isFeatureEnabled("linux.native.menu")) ||
        !JnaLoader.isLoaded() ||
        isUnderVMWithSwiftPluginInstalled()) {
      return null;
    }

    try {
      Path lib = PathManager.findBinFile("libdbm64.so");
      assert lib != null : "DBM lib missing; bin=" + Arrays.toString(new File(PathManager.getBinPath()).list());
      return Native.load(lib.toString(), GlobalMenuLib.class, Collections.singletonMap("jna.encoding", "UTF8"));
    }
    catch (UnsatisfiedLinkError ule) {
      LOG.info("disable global-menu integration because some of shared libraries isn't installed: " + ule);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    return null;
  }

  private static boolean isUnderVMWithSwiftPluginInstalled() {
    // Workaround OC-18001 OC-18634 CLion crashes after opening Swift project on Linux
    if (PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.clion-swift"))) {
      try {
        String stdout = StringUtil.toLowerCase(
          ExecUtil.execAndGetOutput(new GeneralCommandLine("lspci")).getStdout());
        return stdout.contains("vmware") || stdout.contains("virtualbox");
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return false;
  }

  private static class MenuItemInternal {
    final int rootPos;
    final int uid;
    final int type;
    final AnAction action;

    final MenuItemInternal parent;
    final List<MenuItemInternal> children = new ArrayList<>();

    String txt;
    String originTxt;
    char mnemonic;
    boolean isEnabled = true;
    boolean isChecked = false;
    byte[] iconPngBytes;

    int jmodifiers;
    int jkeycode;

    JMenuItem jitem;
    Pointer nativePeer;
    int position = -1;

    long lastFilledMs = 0;
    long lastClearedMs = 0;

    Timer timerClearSwing;

    MenuItemInternal(MenuItemInternal parent, int rootPos, int uid, int type, AnAction action) {
      this.parent = parent;
      this.rootPos = rootPos;
      this.uid = uid;
      this.type = type;
      this.action = action;
    }

    int getPosition() { return position; }

    boolean isRoot() { return rootPos >= 0; }

    boolean isToggleable() { return type == GlobalMenuLib.ITEM_CHECK || type == GlobalMenuLib.ITEM_RADIO; }

    void clearChildrenSwingRefs() {
      for (MenuItemInternal cmi : children) {
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

      final KeyStroke ks = peer.getAccelerator();
      if (ks != null) {
        jkeycode = ks.getKeyCode();
        jmodifiers = ks.getModifiers();
      }
      return res;
    }

    void setLabelFromSwingPeer(@NotNull JMenuItem peer) {
      // exec at EDT
      originTxt = peer.getText();
      txt = originTxt != null ? originTxt : "";
      mnemonic = 0;

      if (originTxt != null && !originTxt.isEmpty()) {
        final int mnemonicCode = peer.getMnemonic();
        final int mnemonicIndex = peer.getDisplayedMnemonicIndex();
        if (mnemonicIndex >= 0 &&
            mnemonicIndex < originTxt.length() &&
            Character.toUpperCase(originTxt.charAt(mnemonicIndex)) == mnemonicCode) {
          final StringBuilder res = new StringBuilder(originTxt);
          res.insert(mnemonicIndex, '_');
          txt = res.toString();
          mnemonic = (char)mnemonicCode;
        }
      }
    }

    void updateNative() {
      // exec at glib-loop thread
      // NOTE: probably it's better to use sync flags, to avoid frequent calls, to avoid applet destabilization)
      if (nativePeer == null) {
        return;
      }

      ourLib.setItemLabel(nativePeer, txt);
      ourLib.setItemEnabled(nativePeer, isEnabled);
      ourLib.setItemIcon(nativePeer, iconPngBytes, iconPngBytes != null ? iconPngBytes.length : 0);
      if (isToggleable()) {
        ourLib.toggleItemStateChecked(nativePeer, isChecked);
      }
      if (jkeycode != 0) {
        final int x11keycode = X11KeyCodes.jkeycode2X11code(jkeycode, 0);
        if (x11keycode != 0) {
          ourLib.setItemShortcut(nativePeer, jmodifiers, x11keycode);
        }
        else if (TRACE_ENABLED) {
          _trace("unknown x11 keycode for jcode=" + jkeycode);
        }
      }
    }

    MenuItemInternal findCorrespondingChild(@NotNull Component target) {
      if (children.isEmpty()) {
        return null;
      }

      if (target instanceof JSeparator) {
        for (MenuItemInternal child : children) {
          if (child.position == -1 && child.action == null) {
            return child;
          }
        }
        return null;
      }

      if (!(target instanceof JMenuItem)) {
        return null;
      }

      final JMenuItem jmi = (JMenuItem)target;

      // find by text
      final String label = jmi.getText();
      if (label != null && !label.isEmpty()) {
        for (MenuItemInternal child : children) {
          if (label.equals(child.originTxt)) {
            return child;
          }
        }
      }

      // find by Action
      AnAction act = null;
      if (target instanceof ActionMenuItem) {
        act = ((ActionMenuItem)target).getAnAction();
      }
      if (target instanceof ActionMenu) {
        act = ((ActionMenu)target).getAnAction();
      }

      if (act == null) {
        return null;
      }

      for (MenuItemInternal child : children) {
        if (act.equals(child.action)) {
          // System.out.println("WARN: can't find child of " + toString() + " corresponding by label '" + String.valueOf(label) + "' (will search by action), all children:\n" + printKids());
          return child;
        }
      }

      return null;
    }

    @SuppressWarnings("unused")
    String printHierarchy() {
      final StringBuilder res = new StringBuilder();
      printHierarchy(res, 0);
      return res.toString();
    }

    void printHierarchy(StringBuilder out, int indent) {
      for (MenuItemInternal kid : children) {
        if (out.length() > 0) {
          out.append('\n');
        }
        for (int c = 0; c < indent; ++c) out.append('\t');
        out.append(kid.toString());
        kid.printHierarchy(out, indent + 1);
      }
    }

    @Override
    public String toString() {
      String res = String.format("'%s' (uid=%d, act=%s)", txt, uid, action);
      if (position == -1) {
        res += " [toDelele]";
      }
      return res;
    }

    String toStringShort() {
      String res = String.format("'%s'", txt);
      if (position == -1) {
        res += " [D]";
      }
      if (isRoot()) {
        res = "Root " + res;
      }
      else {
        res = "Submenu " + res;
      }
      return res;
    }

    void scheduleClearSwing() {
      // exec at glib main-loop thread
      if (timerClearSwing != null) {
        timerClearSwing.restart();
        if (TRACE_CLEARING) _trace("\t reset clear timer of item '%s'", toStringShort());
        return;
      }

      timerClearSwing = new Timer(2000, (e) -> _clearSwing());
      timerClearSwing.setRepeats(false);
      timerClearSwing.start();
      if (TRACE_CLEARING) _trace("\t scheduled (300 ms later) to clear '%s'", toStringShort());
    }

    void cancelClearSwing() {
      // exec at glib main-loop thread
      if (timerClearSwing != null) {
        timerClearSwing.stop();
        timerClearSwing = null;
      }
      for (MenuItemInternal p = parent; p != null; p = p.parent) {
        p.cancelClearSwing();
      }
    }

    private void _clearSwing() {
      // exec at ETD
      if (timerClearSwing == null) {
        return;
      }

      if (jitem == null) {
        if (TRACE_CLEARING) _trace("corresponding (closing) swing item is null - nothing to clear, event source: ", this);
        return;
      }
      if (!(jitem instanceof ActionMenu)) {
        LOG.debug("corresponding (closing) swing item isn't instance of ActionMenu, class=" +
                  jitem.getClass().getName() +
                  ", event source: " +
                  toString());
        return;
      }

      final ActionMenu am = (ActionMenu)jitem;
      am.clearItems();
      clearChildrenSwingRefs();
      _onSwingCleared(System.currentTimeMillis());
      if (TRACE_CLEARING) _trace("\t cleared '%s'", toStringShort());
    }

    private void _onSwingCleared(long timeMs) {
      lastClearedMs = timeMs;
      if (timerClearSwing != null) {
        timerClearSwing.stop();
        timerClearSwing = null;
      }

      for (MenuItemInternal kid : children) {
        kid._onSwingCleared(timeMs);
      }
    }
  }

  private static final class QueuedEvent {
    final int uid;
    final int eventType;
    final int rootId;
    final long timeMs;

    private QueuedEvent(int uid, int eventType, int rootId, long timeMs) {
      this.uid = uid;
      this.eventType = eventType;
      this.rootId = rootId;
      this.timeMs = timeMs;
    }

    static QueuedEvent of(int uid, int eventType, int rootId, long timeMs) {
      return new QueuedEvent(uid, eventType, rootId, timeMs);
    }
  }

  private class EventFilter {
    final private ArrayList<QueuedEvent> myQueued = new ArrayList<>();
    private Timer myTimer;
    private long myClosedMs = 0;

    @SuppressWarnings("unused")
    private GlobalMenuLib.JRunnable myGlibLoopRunnable; // holds runnable object

    private boolean _isClosed() { return myClosedMs > 0; }

    private void _stopTimer() {
      // exec at glib-main-thread
      if (myTimer != null) {
        myTimer.stop();
      }
      myTimer = null;
    }

    private void _processQueue() {
      // exec at glib-main-thread
      for (QueuedEvent q : myQueued) {
        _handleEvent(q.uid, q.eventType, false);
      }
      myQueued.clear();
    }

    private void _startTimer() {
      // exec at glib-main-thread
      _stopTimer();

      final Timer timer = new Timer(50, null);
      timer.addActionListener((e) -> {
        if (TRACE_EVENT_FILTER) _trace("EventFilter: start execution of timer callback");        // exec at EDT
        if (myTimer != timer) {// check that timer wasn't reset
          if (TRACE_EVENT_FILTER) _trace("EventFilter: skip timer-processing because timer was reset (i.e. myTimer == null)");
          return;
        }

        ourLib.execOnMainLoop(myGlibLoopRunnable = () -> {
          // remove continuous series of sequential root events
          // because some of implementations (of menu applet) regularly send packets of root events ('about-to-show' or/and 'open')
          final int lastRootId = myRoots.size() - 1;
          int from = 0;
          while (from < myQueued.size()) {
            final int size = myQueued.size();

            // 1. find event from second root
            while (from < size && myQueued.get(from).rootId != 1) ++from;
            if (from == size) break;

            // 2. rewind to first root
            int first = from - 1;
            if (first < 0 || myQueued.get(first).rootId != 0) {
              // no events from first root, rewind forward
              while (from < size && myQueued.get(from).rootId == 1) ++from;
              continue;
            }
            while (first >= 1 && myQueued.get(first - 1).rootId == 0 && from - first <= 1) --first;

            // 3. find last root
            int to = from + 1;
            while (to < size && myQueued.get(to).rootId != lastRootId) ++to;
            if (to == size) break;
            while (to < size && myQueued.get(to).rootId == lastRootId) ++to;

            // 4. remove fake segment
            if (TRACE_EVENT_FILTER) _trace("EventFilter: remove segment [%d, %d) from queue of size=%d", first, to, myQueued.size());
            myQueued.subList(first, to).clear();
            from = first + 1;
          }

          if (!myQueued.isEmpty()) {
            if (TRACE_ENABLED) _trace("EventFilter: process queued events, size=%d", myQueued.size());
            _processQueue();
          }
          else if (TRACE_ENABLED) _trace("EventFilter: queue is empty");

          myTimer = null;
          myClosedMs = 0; // open filter
          if (TRACE_EVENT_FILTER) _trace("EventFilter: filter is opened");
        });
      });

      myTimer = timer;
      myTimer.setRepeats(false);
      myTimer.start();
      if (TRACE_EVENT_FILTER) _trace("EventFilter: start timer");
    }

    boolean check(int uid, int eventType, @NotNull MenuItemInternal mi) {
      // exec at glib-main-thread
      final boolean isFillEvent = _isFillEvent(eventType);
      final long timeMs = System.currentTimeMillis();

      if (_isClosed()) {
        if (timeMs - myClosedMs > 2000) {
          // simple protection (open filter by timeout)
          if (TRACE_ENABLED) _trace("EventFilter WARNING: close filter by timeout protection");
          _processQueue();
          _stopTimer();
          myClosedMs = 0;
        }
        else {
          // filter is closed
          myQueued.add(QueuedEvent.of(uid, eventType, mi.rootPos, timeMs));
          if (myTimer != null) {
            myTimer.restart();
          }
          return false;
        }
      }

      // filter is opened
      if (mi.rootPos != 0) {
        return true;
      }

      // filter is opened and first root appeared
      if (TRACE_EVENT_FILTER) _trace("EventFilter: close filter");
      myQueued.add(QueuedEvent.of(uid, eventType, mi.rootPos, timeMs));
      myClosedMs = timeMs;
      _startTimer();
      return false;
    }
  }

  private static Object _getPeerField(@NotNull Component object) {
    try {
      Field field = Component.class.getDeclaredField("peer");
      field.setAccessible(true);
      return field.get(object);
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      LOG.error(e);
      return null;
    }
  }

  private static long _getX11WindowXid(@NotNull Window frame) {
    try {
      // getPeer method was removed in jdk9, but 'peer' field still exists
      final ComponentPeer wndPeer = (ComponentPeer)_getPeerField(frame);
      if (wndPeer == null) {
        // wait a little for X11-peer to be connected
        LOG.debug("frame peer is null, wait for connection");
        return 0;
      }

      // sun.awt.X11.XBaseWindow isn't available at all jdks => use reflection
      if (!wndPeer.getClass().getName().equals("sun.awt.X11.XFramePeer")) {
        LOG.debug("frame peer isn't instance of XBaseWindow, class of peer: " + wndPeer.getClass());
        return 0;
      }

      // System.out.println("Window id (from XBaseWindow): 0x" + Long.toHexString(((XBaseWindow)frame.getPeer()).getWindow()));
      final Method method = wndPeer.getClass().getMethod("getWindow");
      if (method == null) {
        return 0;
      }

      return (long)method.invoke(wndPeer);
    }
    catch (Throwable e) {
      LOG.error(e);
      return 0;
    }
  }

  private String _dumpSwingHierarchy() {
    StringBuilder out = new StringBuilder();
    _dumpSwingHierarchy(out);
    return out.toString();
  }

  private void _dumpSwingHierarchy(StringBuilder out) {
    for (MenuItemInternal root : myRoots) {
      final ActionMenu am = (ActionMenu)root.jitem;
      out.append(am.getText());
      out.append('\n');
      _dumpActionMenuKids(am, out, 1);
    }
  }

  private static void _dumpActionMenuKids(@NotNull ActionMenu am, StringBuilder out, int indent) {
    // exec at EDT
    for (Component each : am.getPopupMenu().getComponents()) {
      if (each == null) {
        continue;
      }
      if (!(each instanceof JMenuItem)) {
        continue;
      }

      for (int c = 0; c < indent; ++c) out.append('\t');
      String txt = ((JMenuItem)each).getText();
      if (txt == null || txt.isEmpty()) {
        txt = "null";
      }
      out.append(txt);
      out.append('\n');

      if (each instanceof ActionMenu) {
        _dumpActionMenuKids((ActionMenu)each, out, indent + 1);
      }
    }
  }

  private static void _trace(@NonNls String fmt, Object... args) {
    if (!TRACE_ENABLED) {
      return;
    }
    final String msg = String.format(fmt, args);
    _trace(msg);
  }

  private static void _trace(@NonNls String msg) {
    if (!TRACE_ENABLED) {
      return;
    }
    if (TRACE_SYSOUT)
    //noinspection UseOfSystemOutOrSystemErr
    {
      System.out.println(ourDtf.format(new Date()) + ": " + msg);
    }
    else {
      LOG.info(msg);
    }
  }
}
