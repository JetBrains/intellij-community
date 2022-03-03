// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WindowWatcher implements PropertyChangeListener {
  private static final Logger LOG = Logger.getInstance(WindowWatcher.class);
  private final Object myLock = new Object();
  private final Map<@NotNull Window, WindowInfo> windowToInfo = CollectionFactory.createWeakMap();
  /**
   * Currently focused window (window which has focused component). Can be {@code null} if there is no focused
   * window at all.
   */
  private Window myFocusedWindow;
  /**
   * Contains last focused window for each project.
   */
  private final Set<Window> myFocusedWindows = new HashSet<>();
  @NonNls private static final String FOCUSED_WINDOW_PROPERTY = "focusedWindow";

  WindowWatcher() {}

  /**
   * This method should get notifications about changes of focused window.
   * Only {@code focusedWindow} property is acceptable.
   *
   * @throws IllegalArgumentException if property name isn't {@code focusedWindow}.
   */
  @Override
  public void propertyChange(@NotNull PropertyChangeEvent e) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: propertyChange(" + e + ")");
    }
    if (!FOCUSED_WINDOW_PROPERTY.equals(e.getPropertyName())) {
      throw new IllegalArgumentException("unknown property name: " + e.getPropertyName());
    }

    synchronized (myLock) {
      final Window window = (Window)e.getNewValue();
      if (window == null || ApplicationManager.getApplication().isDisposed()) {
        return;
      }
      if (!windowToInfo.containsKey(window)) {
        windowToInfo.put(window, new WindowInfo(window, true));
      }
      myFocusedWindow = window;
      final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myFocusedWindow));
      for (Iterator<Window> i = myFocusedWindows.iterator(); i.hasNext(); ) {
        final Window w = i.next();
        final DataContext dataContext = DataManager.getInstance().getDataContext(w);
        if (project == CommonDataKeys.PROJECT.getData(dataContext)) {
          i.remove();
        }
      }
      myFocusedWindows.add(myFocusedWindow);
      // Set new root frame
      JFrame frame;
      if (window instanceof JFrame) {
        frame = (JFrame)window;
      }
      else {
        frame = (JFrame)SwingUtilities.getAncestorOfClass(IdeFrameImpl.class, window);
      }
      if (frame != null) {
        JOptionPane.setRootFrame(frame);
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("exit: propertyChange()");
    }
  }

  void dispatchComponentEvent(final ComponentEvent e) {
    int id = e.getID();
    if (WindowEvent.WINDOW_CLOSED == id ||
        (ComponentEvent.COMPONENT_HIDDEN == id && e.getSource() instanceof Window)) {
      dispatchHiddenOrClosed((Window)e.getSource());
    }
    // clear obsolete reference on root frame
    if (WindowEvent.WINDOW_CLOSED == id) {
      Window window = (Window)e.getSource();
      if (JOptionPane.getRootFrame() == window) {
        JOptionPane.setRootFrame(null);
      }
    }
  }

  private void dispatchHiddenOrClosed(final Window window) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: dispatchClosed(" + window + ")");
    }
    synchronized (myLock) {
      WindowInfo info = window == null ? null : windowToInfo.get(window);
      if (info != null) {
        final FocusWatcher focusWatcher = info.myFocusWatcherRef.get();
        if (focusWatcher != null) {
          focusWatcher.deinstall(window);
        }
        windowToInfo.remove(window);
      }
    }
    // Now, we have to recalculate focused window if currently focused
    // window is being closed.
    if (myFocusedWindow == window) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("currently active window should be closed");
      }
      myFocusedWindow = myFocusedWindow.getOwner();
      if (LOG.isDebugEnabled()) {
        LOG.debug("new active window is " + myFocusedWindow);
      }
    }
    for (Iterator<Window> i = myFocusedWindows.iterator(); i.hasNext(); ) {
      Window activeWindow = i.next();
      if (activeWindow == window) {
        final Window newActiveWindow = activeWindow.getOwner();
        i.remove();
        if (newActiveWindow != null) {
          myFocusedWindows.add(newActiveWindow);
        }
        break;
      }
    }
    // Remove invalid infos for garbage collected windows
    for (Iterator<WindowInfo> i = windowToInfo.values().iterator(); i.hasNext(); ) {
      final WindowInfo info = i.next();
      if (info.myFocusWatcherRef.get() == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("remove collected info");
        }
        i.remove();
      }
    }
  }

  public @Nullable Window getFocusedWindow() {
    synchronized (myLock) {
      return myFocusedWindow;
    }
  }

  public @Nullable Component getFocusedComponent(@Nullable Project project) {
    synchronized (myLock) {
      Window window = getFocusedWindowForProject(project);
      if (window == null) {
        return null;
      }
      return getFocusedComponent(window);
    }
  }

  public @Nullable Component getFocusedComponent(@NotNull Window window) {
    synchronized (myLock) {
      WindowInfo info = windowToInfo.get(window);
      if (info == null) { // it means that we don't manage this window, so just return standard focus owner
        // return window.getFocusOwner();
        // TODO[vova,anton] usage of getMostRecentFocusOwner is experimental. But it seems suitable here.
        return window.getMostRecentFocusOwner();
      }

      FocusWatcher focusWatcher = info.myFocusWatcherRef.get();
      if (focusWatcher != null) {
        final Component focusedComponent = focusWatcher.getFocusedComponent();
        if (focusedComponent != null && focusedComponent.isShowing()) {
          return focusedComponent;
        }
        else {
          return window == KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() ? window : null;
        }
      }
      else {
        // info isn't valid, i.e. window was garbage collected, so we need the remove invalid info
        // and return null
        windowToInfo.remove(window);
        return null;
      }
    }
  }

  public @Nullable FocusWatcher getFocusWatcherFor(Component c) {
    final Window window = SwingUtilities.getWindowAncestor(c);
    final WindowInfo info = window == null ? null : windowToInfo.get(window);
    return info == null ? null : info.myFocusWatcherRef.get();
  }

  /**
   * @param project may be null (for example, if no projects are opened)
   */
  public @Nullable Window suggestParentWindow(@Nullable Project project, @NotNull WindowManagerEx windowManager) {
    synchronized (myLock) {
      Window window = getFocusedWindowForProject(project);
      if (window == null) {
        if (project == null) {
          Project[] projects = ProjectUtil.getOpenProjects();
          if (projects.length == 1) {
            project = projects[0];
          }
        }
        if (project == null) {
          return null;
        }
        else {
          IdeFrame frame = windowManager.findFrameFor(project);
          if (frame == null) {
            return null;
          }
          else if (frame instanceof Window) {
            return (Window)frame;
          }
          else {
            return ((ProjectFrameHelper)frame).getFrame();
          }
        }
      }

      LOG.assertTrue(window.isDisplayable());
      LOG.assertTrue(window.isShowing());

      while (window != null) {
        // skip not visible and disposed/not shown windows
        if (!window.isDisplayable() || !window.isShowing()) {
          window = window.getOwner();
          continue;
        }
        // skip windows that have not associated WindowInfo
        final WindowInfo info = windowToInfo.get(window);
        if (info == null) {
          window = window.getOwner();
          continue;
        }
        if (info.mySuggestAsParent) {
          return window;
        }
        else {
          window = window.getOwner();
        }
      }
      return null;
    }
  }

  public boolean isNotSuggestAsParent(@NotNull Window window) {
    synchronized (myLock) {
      WindowInfo info = windowToInfo.get(window);
      return info != null && !info.mySuggestAsParent;
    }
  }

  public void doNotSuggestAsParent(@NotNull Window window) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: doNotSuggestAsParent(" + window + ")");
    }
    synchronized (myLock) {
      final WindowInfo info = windowToInfo.get(window);
      if (info == null) {
        windowToInfo.put(window, new WindowInfo(window, false));
      }
      else {
        info.mySuggestAsParent = false;
      }
    }
  }

  /**
   * @return active window for specified {@code project}. There is only one window
   * for project can be at any point of time.
   */
  private @Nullable Window getFocusedWindowForProject(final @Nullable Project project) {
    //todo[anton,vova]: it is possible that returned wnd is not contained in myFocusedWindows; investigate
    outer:
    for (Window window : myFocusedWindows) {
      while (!window.isDisplayable() || !window.isShowing()) { // if window isn't visible then gets its first visible ancestor
        window = window.getOwner();
        if (window == null) {
          continue outer;
        }
      }
      final DataContext dataContext = DataManager.getInstance().getDataContext(window);
      if (project == CommonDataKeys.PROJECT.getData(dataContext)) {
        return window;
      }
    }
    return null;
  }

  private static final class WindowInfo {
    final WeakReference<FocusWatcher> myFocusWatcherRef;
    boolean mySuggestAsParent;

    WindowInfo(final Window window, final boolean suggestAsParent) {
      final FocusWatcher focusWatcher = new FocusWatcher();
      focusWatcher.install(window);
      myFocusWatcherRef = new WeakReference<>(focusWatcher);
      mySuggestAsParent = suggestAsParent;
    }
  }
}
