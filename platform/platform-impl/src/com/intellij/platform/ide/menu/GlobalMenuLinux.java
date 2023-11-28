// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu;

import com.intellij.diagnostic.LoadingState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.UISettings;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
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
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.LinuxGlobalMenuEventHandler;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.JavaCoroutines;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.system.CpuArch;
import com.intellij.util.ui.ImageUtil;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.peer.ComponentPeer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

interface GlobalMenuLib extends Library {
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
  private static final GlobalMenuLib.JRunnable updateAllRoots;
  private static final GlobalMenuLib.JRunnable onAppmenuServiceAppeared;
  private static final GlobalMenuLib.JRunnable onAppmenuServiceVanished;
  private static final Set<GlobalMenuLinux> instances = ConcurrentHashMap.newKeySet();
  private static final int STAT_CREATED = 0;
  private static final int STAT_DELETED = 1;
  private static final int STAT_UPDATED = 2;
  static final MutableStateFlow<Boolean> isPresentedMutable = StateFlowKt.MutableStateFlow(false);

  static {
    ourLib = _loadLibrary();
    if (ourLib == null) {
      ourGLogger = null;
      updateAllRoots = null;
      onAppmenuServiceAppeared = null;
      onAppmenuServiceVanished = null;
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
      updateAllRoots = () -> {
        // exec at glib-thread
        if (!isPresentedMutable.getValue()) {
          return;
        }

        for (GlobalMenuLinux gml : instances) {
          gml._updateRoots();
        }
      };
      onAppmenuServiceAppeared = () -> {
        // exec at glib-thread
        LOG.info("Appeared dbus-service 'com.canonical.AppMenu.Registrar'");
        isPresentedMutable.setValue(true);
        updateAllRoots.run();
      };
      onAppmenuServiceVanished = () -> {
        // exec at glib-thread
        LOG.info("Closed dbus-service 'com.canonical.AppMenu.Registrar'");
        isPresentedMutable.setValue(false);
        for (GlobalMenuLinux menuLinux : instances) {
          menuLinux.windowHandle = null;
          EventQueue.invokeLater(() -> {
            setIdeMenuVisible(menuLinux.frame, true);
          });
        }
      };

      String threadName = "GlobalMenuLinux loop";
      new Thread(() -> ourLib.runMainLoop(ourGLogger, onAppmenuServiceAppeared, onAppmenuServiceVanished), threadName).start();
      LOG.info("Start glib main loop in thread: " + threadName);
    }
  }

  private final @NotNull JFrame frame;
  private final GlobalMenuLib.JRunnable onWindowReleased;
  private final EventFilter eventFilter = new EventFilter();
  private final AtomicLong xid = new AtomicLong(0);
  private List<MenuItemInternal> roots;
  private Pointer windowHandle;
  private boolean isEnabled = true;
  private boolean myIsDisposed = false;
  // don't filter a first packet of events (it causes slow reaction of KDE applet)
  private boolean isFirstFilling = true;

  private GlobalMenuLinux(@NotNull JFrame frame) {
    assert ourLib != null;
    LOG.info("created instance of GlobalMenuLinux for frame: " + frame);

    final long xid = _getX11WindowXid(frame);
    this.xid.set(xid);
    if (xid == 0) {
      frame.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
          frame.removeComponentListener(this);
          GlobalMenuLinux.this.xid.set(_getX11WindowXid(frame));
        }
      });
    }

    this.frame = frame;
    onWindowReleased = () -> {
      // exec at glib-thread
      windowHandle = null;
      if (roots != null) {
        for (MenuItemInternal root : roots) {
          root.nativePeer = null;
          root.children.clear();
        }
      }
      if (myIsDisposed) {
        instances.remove(this);
      }
    };

    if (SystemInfo.isKDE && !KDE_DISABLE_ROOT_MNEMONIC_PROCESSING) {
      // root menu items doesn't catch mnemonic shortcuts (in KDE), so process them inside IDE
      IdeEventQueue.getInstance().addDispatcher(e -> {
        if (!(e instanceof KeyEvent event)) {
          return false;
        }

        if (!event.isAltDown()) {
          return false;
        }

        final Component src = event.getComponent();
        final Window wndParent = src instanceof Window ? (Window)src : SwingUtilities.windowForComponent(src);
        final char eventChar = Character.toUpperCase(event.getKeyChar());

        for (GlobalMenuLinux gml : instances) {
          if (gml.frame == wndParent) {
            List<MenuItemInternal> currentRoots = gml.roots;
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

    instances.add(this);
  }

  @Override
  public void dispose() {
    // exec at EDT
    if (myIsDisposed) {
      return;
    }

    myIsDisposed = true;

    if (windowHandle != null) {
      _trace("scheduled destroying of GlobalMenuLinux for frame %s | xid=0x%X", frame, xid);
      ourLib.releaseWindowOnMainLoop(windowHandle, onWindowReleased);
    }
    else {
      _trace("scheduled destroying of unused GlobalMenuLinux for frame %s | xid=0x%X", frame, xid);
      ourLib.execOnMainLoop(onWindowReleased);
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
    if (windowHandle != null) {
      _trace("bind new window 0x%X", xid);
      ourLib.bindNewWindow(windowHandle, xid);
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
    if (windowHandle != null) {
      _trace("unbind window 0x%X", xid);
      ourLib.unbindWindow(windowHandle, xid);
    }
  }

  public void setRoots(List<ActionMenu> roots) {
    // exec at EDT
    if (ourLib == null) {
      return;
    }

    ThreadingAssertions.assertEventDispatchThread();

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
          am.setSelected(true);
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

    this.roots = newRoots;
    _trace("set new menu roots, count=%d", size);
    ourLib.execOnMainLoop(updateAllRoots);
  }

  @RequiresEdt
  private static void setIdeMenuVisible(@NotNull JFrame frame, boolean visible) {
    ThreadingAssertions.assertEventDispatchThread();
    JRootPane rootPane = frame.getRootPane();

    boolean mainMenuApplicable = ExperimentalUI.isNewUI()
                                 ? !IdeRootPane.getHideNativeLinuxTitle() && !IdeRootPane.isMenuButtonInToolbar()
                                 : UISettings.getInstance().getShowMainMenu();
    if (rootPane != null && mainMenuApplicable) {
      rootPane.getJMenuBar().setVisible(visible);
    }
  }

  private void _updateRoots() {
    // exec at glib-thread
    if (!isEnabled || myIsDisposed) {
      return;
    }
    if (xid.get() == 0) {
      LOG.debug("can´t update roots of frame " + frame + " because xid == 0");
      return;
    }

    if (windowHandle == null) {
      windowHandle = ourLib.registerWindow(xid.get(), this);
      if (windowHandle == null) {
        LOG.error("AppMenu-service can't register xid " + xid);
        return;
      }
    }

    ourLib.clearRootMenu(windowHandle);

    List<MenuItemInternal> cRoots = roots;
    if (cRoots == null || cRoots.isEmpty()) {
      return;
    }

    for (MenuItemInternal mi : cRoots) {
      mi.nativePeer = ourLib.addRootMenu(windowHandle, mi.uid, mi.txt);
      _processChildren(mi);
    }

    if (!SHOW_SWING_MENU) {
      EventQueue.invokeLater(() -> {
        if (isEnabled) {
          setIdeMenuVisible(frame, false);
        }
      });
    }
  }

  public void toggle(boolean enabled) {
    if (ourLib == null || myIsDisposed) {
      return;
    }

    if (xid.get() == 0) {
      LOG.debug("can´t toggle global-menu of frame " + frame + " because xid == 0");
      return;
    }

    if (isEnabled == enabled) {
      return;
    }

    isEnabled = enabled;

    if (enabled) {
      _trace("enable global-menu");
      ourLib.execOnMainLoop(updateAllRoots);
    }
    else {
      if (windowHandle != null) {
        _trace("disable global menu, scheduled destroying of GlobalMenuLinux for xid=0x%X", xid);
        ourLib.releaseWindowOnMainLoop(windowHandle, onWindowReleased);
      }

      ApplicationManager.getApplication().invokeLater(() -> setIdeMenuVisible(frame, true));
    }
  }

  private MenuItemInternal _findMenuItem(int uid) {
    return _findMenuItem(roots, uid);
  }

  @Override
  public void handleEvent(int uid, int eventType) {
    _handleEvent(uid, eventType, true);
  }

  private void _handleEvent(int uid, int eventType, boolean doFiltering) {
    // glib main-loop thread
    if (windowHandle == null || myIsDisposed) {
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

    if (!DISABLE_EVENTS_FILTERING && !isFirstFilling && doFiltering && !eventFilter.check(uid, eventType, mi)) {
      return;
    }

    if (isFirstFilling) {
      final Timer timer = new Timer(5000, e -> isFirstFilling = false);
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
                "corresponding (opening) swing item is null, event source: " +
                mi +
                ", swing menu hierarchy:\n" +
                _dumpSwingHierarchy());
            }
            return;
          }
          if (!(jmi instanceof ActionMenu am)) {
            LOG.debug("corresponding (opening) swing item isn't instance of ActionMenu, class=" +
                      jmi.getClass().getName() +
                      ", event source: " +
                      mi);
            return;
          }

          mi.lastFilledMs = timeMs;

          am.removeAll();
          am.setSelected(true);
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
      if (!(jmi instanceof ActionMenuItem ami)) {
        LOG.debug("corresponding (clicked) swing item isn't instance of ActionMenuItem, class=" +
                  jmi.getClass().getName() +
                  ", event source: " +
                  mi);
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> ami.doClick());
    }
  }

  private String _dumpSwingHierarchy() {
    StringBuilder out = new StringBuilder();
    _dumpSwingHierarchy(out);
    return out.toString();
  }

  private void _dumpSwingHierarchy(StringBuilder out) {
    for (MenuItemInternal root : roots) {
      final ActionMenu am = (ActionMenu)root.jitem;
      out.append(am.getText());
      out.append('\n');
      _dumpActionMenuKids(am, out, 1);
    }
  }

  public static @NotNull GlobalMenuLinux create(@NotNull JFrame frame) {
    return new GlobalMenuLinux(frame);
  }

  private static MenuItemInternal _findMenuItem(List<MenuItemInternal> kids, int uid) {
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
    return switch (eventType) {
      case GlobalMenuLib.EVENT_OPENED -> "event-opened";
      case GlobalMenuLib.EVENT_CLOSED -> "event-closed";
      case GlobalMenuLib.EVENT_CLICKED -> "event-clicked";
      case GlobalMenuLib.SIGNAL_ABOUT_TO_SHOW -> "signal-about-to-show";
      case GlobalMenuLib.SIGNAL_ACTIVATED -> "signal-activated";
      case GlobalMenuLib.SIGNAL_CHILD_ADDED -> "signal-child-added";
      case GlobalMenuLib.SIGNAL_SHOWN -> "signal-shown";
      default -> "unknown-event-type-" + eventType;
    };
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
    if (each instanceof ActionMenuItem ami) {
      result = new MenuItemInternal(parent, -1, System.identityHashCode(ami),
                                    ami.isToggleable() ? GlobalMenuLib.ITEM_CHECK : GlobalMenuLib.ITEM_SIMPLE, ami.getAnAction());
      result.jitem = ami;
    }
    else if (each instanceof ActionMenu am2) {
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
        if (deepness > 1 && (each instanceof ActionMenu jmiEach)) {
          jmiEach.removeAll();
          jmiEach.setSelected(true);
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

  // return true when native library was loaded (and dependent packages like glib-dbusmenu were installed)
  public static boolean isAvailable() { return ourLib != null; }

  // return true when corresponding dbus-service is alive
  public static boolean isPresented() {
    return ourLib != null && isPresentedMutable.getValue();
  }

  private static GlobalMenuLib _loadLibrary() {
    Application app;
    if (!SystemInfoRt.isLinux ||
        !(CpuArch.isIntel64() || CpuArch.isArm64()) ||
        (app = ApplicationManager.getApplication()) == null || app.isUnitTestMode() || app.isHeadlessEnvironment() ||
        Boolean.getBoolean("linux.native.menu.force.disable") ||
        (LoadingState.COMPONENTS_REGISTERED.isOccurred() && !Experiments.getInstance().isFeatureEnabled("linux.native.menu")) ||
        !JnaLoader.isLoaded() ||
        isUnderVMWithSwiftPluginInstalled()) {
      return null;
    }

    try {
      @SuppressWarnings("SpellCheckingInspection") Path lib = PathManager.findBinFile("libdbm.so");
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

  private static long _getX11WindowXid(@NotNull Window frame) {
    try {
      // getPeer method was removed in jdk9, but 'peer' field still exists
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      ComponentPeer componentPeer = (ComponentPeer)MethodHandles.privateLookupIn(Component.class, lookup)
        .findVarHandle(Component.class, "peer", ComponentPeer.class)
        .get(frame);
      if (componentPeer == null) {
        // wait a little for X11-peer to be connected
        LOG.debug("frame peer is null, wait for connection");
        return 0;
      }

      // sun.awt.X11.XBaseWindow isn't available at all jdks => use reflection
      Class<? extends ComponentPeer> componentPeerClass = componentPeer.getClass();
      if (!componentPeerClass.getName().equals("sun.awt.X11.XFramePeer")) {
        LOG.info("frame peer isn't instance of XBaseWindow, class of peer: " + componentPeerClass);
        return 0;
      }

      // System.out.println("Window id (from XBaseWindow): 0x" + Long.toHexString(((XBaseWindow)frame.getPeer()).getWindow()));
      return (long)MethodHandles.privateLookupIn(componentPeerClass, lookup)
        .findVirtual(componentPeerClass, "getWindow", MethodType.methodType(Long.TYPE))
        .invoke(componentPeer);
    }
    catch (Throwable e) {
      LOG.error(e);
      return 0;
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

      out.append("\t".repeat(Math.max(0, indent)));
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
    if (TRACE_SYSOUT) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(ourDtf.format(new Date()) + ": " + msg);
    }
    else {
      LOG.info(msg);
    }
  }

  static final class MyActionTuner implements ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
    @Override
    public @Nullable Object customize(@NotNull ActionRuntimeRegistrar actionRegistrar, @NotNull Continuation<? super Unit> $completion) {
      return JavaCoroutines.suspendJava(jc -> {
        doCustomize(actionRegistrar);
        jc.resume(Unit.INSTANCE);
      }, $completion);
    }

    private static void doCustomize(@NotNull ActionRuntimeRegistrar actionRegistrar) {
      if (!SystemInfoRt.isLinux || ApplicationManager.getApplication().isUnitTestMode() || !isPresented()) {
        return;
      }

      // register toggle-swing-menu action (to be able to enable swing menu when system applet is died)
      actionRegistrar
        .registerAction(TOGGLE_SWING_MENU_ACTION_ID, new AnAction(IdeBundle.message("action.toggle.global.menu.integration.text"),
                                                                  IdeBundle.message(
                                                                    "action.enable.disable.global.menu.integration.description"), null) {
          boolean enabled = false;

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            for (GlobalMenuLinux gml : instances) {
              gml.toggle(enabled);
            }
            enabled = !enabled;
          }
        });
    }
  }

  private static final class MenuItemInternal {
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
      if (nativePeer == null || ourLib == null) {
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

      if (!(target instanceof JMenuItem jmi)) {
        return null;
      }

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
        if (!out.isEmpty()) {
          out.append('\n');
        }
        out.append("\t".repeat(Math.max(0, indent)));
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
      if (!(jitem instanceof ActionMenu am)) {
        LOG.debug("corresponding (closing) swing item isn't instance of ActionMenu, " +
                  "class=" + jitem.getClass().getName() + ", event source: " + this);
        return;
      }

      am.clearItems();
      am.setSelected(false);
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

  private record QueuedEvent(int uid, int eventType, int rootId, long timeMs) {
  }

  private final class EventFilter {
    private final ArrayList<QueuedEvent> queued = new ArrayList<>();
    private Timer myTimer;
    private long closedMs = 0;

    @SuppressWarnings("unused")
    private GlobalMenuLib.JRunnable glibLoopRunnable; // holds runnable object

    private boolean _isClosed() { return closedMs > 0; }

    private void _stopTimer() {
      // exec at glib-main-thread
      if (myTimer != null) {
        myTimer.stop();
      }
      myTimer = null;
    }

    private void _processQueue() {
      // exec at glib-main-thread
      for (QueuedEvent q : queued) {
        _handleEvent(q.uid, q.eventType, false);
      }
      queued.clear();
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

        ourLib.execOnMainLoop(glibLoopRunnable = () -> {
          // remove continuous series of sequential root events
          // because some implementations (of menu applet) regularly send packets of root events ('about-to-show' or/and 'open')
          int lastRootId = roots.size() - 1;
          int from = 0;
          while (from < queued.size()) {
            int size = queued.size();
            // 1. find event from second root
            while (from < size && queued.get(from).rootId != 1) {
              ++from;
            }

            if (from == size) {
              break;
            }

            // 2. rewinds to first root
            int first = from - 1;
            if (first < 0 || queued.get(first).rootId != 0) {
              // no events from first root, rewind forward
              while (from < size && queued.get(from).rootId == 1) ++from;
              continue;
            }

            while (first >= 1 && queued.get(first - 1).rootId == 0 && from - first <= 1) {
              --first;
            }

            // 3. find last root
            int to = from + 1;
            while (to < size && queued.get(to).rootId != lastRootId) {
              ++to;
            }

            if (to == size) {
              break;
            }

            while (to < size && queued.get(to).rootId == lastRootId) {
              ++to;
            }

            // 4. remove fake segment
            if (TRACE_EVENT_FILTER) {
              _trace("EventFilter: remove segment [%d, %d) from queue of size=%d", first, to, queued.size());
            }
            queued.subList(first, to).clear();
            from = first + 1;
          }

          if (!queued.isEmpty()) {
            if (TRACE_ENABLED) {
              _trace("EventFilter: process queued events, size=%d", queued.size());
            }
            _processQueue();
          }
          else if (TRACE_ENABLED) {
            _trace("EventFilter: queue is empty");
          }

          myTimer = null;
          closedMs = 0; // open filter
          if (TRACE_EVENT_FILTER) {
            _trace("EventFilter: filter is opened");
          }
        });
      });

      myTimer = timer;
      myTimer.setRepeats(false);
      myTimer.start();
      if (TRACE_EVENT_FILTER) {
        _trace("EventFilter: start timer");
      }
    }

    boolean check(int uid, int eventType, @NotNull MenuItemInternal mi) {
      // exec at glib-main-thread
      final long timeMs = System.currentTimeMillis();

      if (_isClosed()) {
        if (timeMs - closedMs > 2000) {
          // simple protection (open filter by timeout)
          if (TRACE_ENABLED) _trace("EventFilter WARNING: close filter by timeout protection");
          _processQueue();
          _stopTimer();
          closedMs = 0;
        }
        else {
          // filter is closed
          queued.add(new QueuedEvent(uid, eventType, mi.rootPos, timeMs));
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
      queued.add(new QueuedEvent(uid, eventType, mi.rootPos, timeMs));
      closedMs = timeMs;
      _startTimer();
      return false;
    }
  }
}
