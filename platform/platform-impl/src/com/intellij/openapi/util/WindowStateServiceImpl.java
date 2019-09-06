// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.FrameState;
import com.intellij.ui.ScreenUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Sergey.Malenkov
 */
abstract class WindowStateServiceImpl extends WindowStateService implements PersistentStateComponent<Element> {
  @NonNls private static final String KEY = "key";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String MAXIMIZED = "maximized";
  @NonNls private static final String FULL_SCREEN = "full-screen";
  @NonNls private static final String TIMESTAMP = "timestamp";
  @NonNls private static final String SCREEN = "screen";

  private static final Logger LOG = Logger.getInstance(WindowStateService.class);
  private final Map<String, WindowState> myStateMap = new TreeMap<>();

  protected WindowStateServiceImpl(@Nullable Project project) {
    super(project);
  }

  abstract Point getDefaultLocationFor(@NotNull String key);

  abstract Dimension getDefaultSizeFor(@NotNull String key);

  abstract Rectangle getDefaultBoundsFor(@NotNull String key);

  abstract boolean getDefaultMaximizedFor(Object object, @NotNull String key);

  @Override
  public final Element getState() {
    Element element = new Element(STATE);
    synchronized (myStateMap) {
      for (Map.Entry<String, WindowState> entry : myStateMap.entrySet()) {
        String key = entry.getKey();
        if (key != null) {
          WindowState state = entry.getValue();
          Element child = new Element(STATE);
          if (state.myLocation != null) {
            JDOMUtil.setLocation(child, state.myLocation);
          }
          if (state.mySize != null) {
            JDOMUtil.setSize(child, state.mySize);
          }
          if (state.myMaximized) {
            child.setAttribute(MAXIMIZED, Boolean.toString(true));
          }
          if (state.myFullScreen) {
            child.setAttribute(FULL_SCREEN, Boolean.toString(true));
          }
          if (state.myScreen != null) {
            child.addContent(JDOMUtil.setBounds(new Element(SCREEN), state.myScreen));
          }
          child.setAttribute(KEY, key);
          child.setAttribute(TIMESTAMP, Long.toString(state.myTimeStamp));
          element.addContent(child);
        }
      }
    }
    return element;
  }

  @Override
  public final void loadState(@NotNull Element element) {
    synchronized (myStateMap) {
      myStateMap.clear();
      for (Element child : element.getChildren()) {
        if (!STATE.equals(child.getName())) continue; // ignore unexpected element

        long current = System.currentTimeMillis();
        long timestamp = StringUtilRt.parseLong(child.getAttributeValue(TIMESTAMP), current);
        if (TimeUnit.DAYS.toMillis(100) <= (current - timestamp)) continue; // ignore old elements

        String key = child.getAttributeValue(KEY);
        if (StringUtilRt.isEmpty(key)) continue; // unexpected key

        Point location = JDOMUtil.getLocation(child);
        Dimension size = JDOMUtil.getSize(child);
        if (location == null && size == null) continue; // unexpected value

        WindowState state = new WindowState();
        state.myLocation = location;
        state.mySize = size;
        state.myMaximized = Boolean.parseBoolean(child.getAttributeValue(MAXIMIZED));
        state.myFullScreen = Boolean.parseBoolean(child.getAttributeValue(FULL_SCREEN));
        state.myScreen = apply(JDOMUtil::getBounds, child.getChild(SCREEN));
        state.myTimeStamp = timestamp;
        myStateMap.put(key, state);
      }
    }
  }

  @Override
  public boolean loadStateFor(Object object, @NotNull String key, @NotNull Component component) {
    Point location = null;
    Dimension size = null;
    boolean maximized = false;
    WindowState state = getFor(object, key, WindowState.class);
    if (state != null) {
      location = state.myLocation;
      size = state.mySize;
      maximized = state.myMaximized;
    }
    if (location == null && size == null) {
      location = getDefaultLocationFor(key);
      size = getDefaultSizeFor(key);
      if (!isVisible(location, size)) {
        return false;
      }
      maximized = getDefaultMaximizedFor(object, key);
    }
    Frame frame = component instanceof Frame ? (Frame)component : null;
    if (frame != null && Frame.NORMAL != frame.getExtendedState()) {
      frame.setExtendedState(Frame.NORMAL);
    }
    Rectangle bounds = component.getBounds();
    if (location != null) {
      bounds.setLocation(location);
    }
    if (size != null) {
      bounds.setSize(size);
    }
    if (bounds.isEmpty()) {
      bounds.setSize(component.getPreferredSize());
    }
    component.setBounds(bounds);
    if (maximized && frame != null) {
      frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }
    return true;
  }

  @Override
  public void saveStateFor(Object object, @NotNull String key, @NotNull Component component) {
    FrameState state = FrameState.getFrameState(component);
    putFor(object, key, state.getLocation(), true, state.getSize(), true, state.isMaximized(), true, state.isFullScreen(), true);
  }

  @Override
  public Point getLocationFor(Object object, @NotNull String key) {
    Point location = getFor(object, key, Point.class);
    return location != null ? location : getDefaultLocationFor(key);
  }

  @Override
  public void putLocationFor(Object object, @NotNull String key, Point location) {
    putFor(object, key, location, true, null, false, false, false, false, false);
  }

  @Override
  public Dimension getSizeFor(Object object, @NotNull String key) {
    Dimension size = getFor(object, key, Dimension.class);
    return size != null ? size : getDefaultSizeFor(key);
  }

  @Override
  public void putSizeFor(Object object, @NotNull String key, Dimension size) {
    putFor(object, key, null, false, size, true, false, false, false, false);
  }

  @Override
  public Rectangle getBoundsFor(Object object, @NotNull String key) {
    Rectangle bounds = getFor(object, key, Rectangle.class);
    return bounds != null ? bounds : getDefaultBoundsFor(key);
  }

  @Override
  public void putBoundsFor(Object object, @NotNull String key, Rectangle bounds) {
    Point location = apply(Rectangle::getLocation, bounds);
    Dimension size = apply(Rectangle::getSize, bounds);
    putFor(object, key, location, true, size, true, false, false, false, false);
  }

  private <T> T getFor(Object object, @NotNull String key, @NotNull Class<T> type) {
    if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) return null;
    GraphicsConfiguration configuration = getConfiguration(object);
    synchronized (myStateMap) {
      WindowState state = myStateMap.get(getAbsoluteKey(configuration, key));
      if (isVisible(state)) return state.get(type, null);

      state = myStateMap.get(key);
      return state == null ? null : state.get(type, state.myScreen == null ? null : getScreenRectangle(configuration));
    }
  }

  private void putFor(Object object, @NotNull String key,
                      Point location, boolean locationSet,
                      Dimension size, boolean sizeSet,
                      boolean maximized, boolean maximizedSet,
                      boolean fullScreen, boolean fullScreenSet) {
    if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) return;
    GraphicsConfiguration configuration = getConfiguration(object);
    synchronized (myStateMap) {
      put(getAbsoluteKey(configuration, key), location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet);

      WindowState state = put(key, location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet);
      if (state != null) {
        // update a screen to adjust stored state
        state.myScreen = location == null
                         ? getScreenRectangle(configuration)
                         : size == null
                           ? ScreenUtil.getScreenRectangle(location)
                           : ScreenUtil.getScreenRectangle(location.x + size.width / 2, location.y + size.height / 2);
      }
    }
  }

  @Nullable
  private WindowState put(@NotNull String key,
                          @Nullable Point location, boolean locationSet,
                          @Nullable Dimension size, boolean sizeSet,
                          boolean maximized, boolean maximizedSet,
                          boolean fullScreen, boolean fullScreenSet) {
    WindowState state = myStateMap.get(key);
    if (state == null) {
      state = new WindowState();
      if (!state.set(location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet)) return null;
      myStateMap.put(key, state);
      return state;
    }
    else {
      if (state.set(location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet)) return state;
      myStateMap.remove(key);
      return null;
    }
  }

  @NotNull
  private static String getAbsoluteKey(@Nullable GraphicsConfiguration configuration, @NotNull String key) {
    StringBuilder sb = new StringBuilder(key);
    GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    for (GraphicsDevice device : environment.getScreenDevices()) {
      Rectangle bounds = getScreenRectangle(device.getDefaultConfiguration());
      sb.append('/').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    if (configuration != null) {
      Rectangle bounds = getScreenRectangle(configuration);
      sb.append('@').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    return sb.toString();
  }

  @Nullable
  private static GraphicsConfiguration getConfiguration(@Nullable Object object) {
    if (object instanceof Project) {
      Project project = (Project)object;
      object = WindowManager.getInstance().getFrame(project);
      if (object == null) LOG.warn("cannot find a project frame for " + project);
    }
    if (object instanceof Window) {
      Window window = (Window)object;
      GraphicsConfiguration configuration = window.getGraphicsConfiguration();
      if (configuration != null) return configuration;
      object = ScreenUtil.getScreenDevice(window.getBounds());
      if (object == null) LOG.warn("cannot find a device for " + window);
    }
    if (object instanceof GraphicsDevice) {
      GraphicsDevice device = (GraphicsDevice)object;
      object = device.getDefaultConfiguration();
      if (object == null) LOG.warn("cannot find a configuration for " + device);
    }
    if (object instanceof GraphicsConfiguration) return (GraphicsConfiguration)object;
    if (object != null) LOG.warn("unexpected object " + object.getClass());
    return null;
  }

  @NotNull
  private static Rectangle getScreenRectangle(@Nullable GraphicsConfiguration configuration) {
    return configuration != null
           ? ScreenUtil.getScreenRectangle(configuration)
           : ScreenUtil.getMainScreenBounds();
  }

  private static final class WindowState {
    private Rectangle myScreen;
    private Point myLocation;
    private Dimension mySize;
    private boolean myMaximized;
    private boolean myFullScreen;
    private long myTimeStamp;

    @SuppressWarnings("unchecked")
    <T> T get(@NotNull Class<T> type, @Nullable Rectangle screen) {
      // copy a current location only if it is needed
      Point location = type == Dimension.class ? null : apply(Point::new, myLocation);
      if (location == null && (type == Rectangle.class || type == Point.class)) return null;
      // copy a current size only if it is needed
      Dimension size = type == Point.class ? null : apply(Dimension::new, mySize);
      if (size == null && (type == Rectangle.class || type == Dimension.class)) return null;
      // convert location and size according to the given screen
      if (myScreen != null && screen != null && !screen.isEmpty()) {
        double w = myScreen.getWidth() / screen.getWidth();
        double h = myScreen.getHeight() / screen.getHeight();
        if (location != null) location.setLocation(screen.x + (location.x - myScreen.x) / w, screen.y + (location.y - myScreen.y) / h);
        if (size != null) size.setSize(size.width / w, size.height / h);
      }
      if (type == Point.class) return (T)location;
      if (type == Dimension.class) return (T)size;
      if (type == Rectangle.class) return (T)new Rectangle(location, size);
      if (type != WindowState.class) throw new IllegalArgumentException();
      // copy a current state
      WindowState state = new WindowState();
      state.myLocation = location;
      state.mySize = size;
      state.myMaximized = myMaximized;
      state.myFullScreen = myFullScreen;
      return (T)state;
    }

    private boolean set(Point location, boolean locationSet,
                        Dimension size, boolean sizeSet,
                        boolean maximized, boolean maximizedSet,
                        boolean fullScreen, boolean fullScreenSet) {
      if (locationSet) {
        myLocation = apply(Point::new, location);
      }
      if (sizeSet) {
        mySize = apply(Dimension::new, size);
      }
      if (maximizedSet) {
        myMaximized = maximized;
      }
      if (fullScreenSet) {
        myFullScreen = fullScreen;
      }
      if (myLocation == null && mySize == null) return false;
      // update timestamp of modified state
      myTimeStamp = System.currentTimeMillis();
      return true;
    }
  }

  private static boolean isVisible(WindowState state) {
    return state != null && isVisible(state.myLocation, state.mySize);
  }

  private static boolean isVisible(Point location, Dimension size) {
    if (location == null) {
      return size != null;
    }
    if (ScreenUtil.isVisible(location)) {
      return true;
    }
    if (size == null) {
      return false;
    }
    return ScreenUtil.isVisible(new Rectangle(location, size));
  }

  @Nullable
  private static <T, R> R apply(@NotNull Function<T, R> function, @Nullable T value) {
    return value == null ? null : function.apply(value);
  }
}
