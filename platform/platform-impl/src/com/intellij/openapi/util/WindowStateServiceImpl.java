// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

abstract class WindowStateServiceImpl extends WindowStateService implements ModificationTracker, PersistentStateComponent<Element> {
  @NonNls private static final String KEY = "key";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String MAXIMIZED = "maximized";
  @NonNls private static final String FULL_SCREEN = "full-screen";
  @NonNls private static final String TIMESTAMP = "timestamp";
  @NonNls private static final String SCREEN = "screen";

  private static final Logger LOG = Logger.getInstance(WindowStateService.class);
  private final AtomicLong myModificationCount = new AtomicLong();
  private final Map<String, Runnable> myRunnableMap = new TreeMap<>();
  private final Map<String, CachedState> myStateMap = new TreeMap<>();

  protected WindowStateServiceImpl(@Nullable Project project) {
    super(project);
  }

  @Override
  public long getModificationCount() {
    synchronized (myRunnableMap) {
      myRunnableMap.values().forEach(Runnable::run);
    }
    return myModificationCount.get();
  }

  @Override
  public final Element getState() {
    Element element = new Element(STATE);
    synchronized (myStateMap) {
      for (Map.Entry<String, CachedState> entry : myStateMap.entrySet()) {
        String key = entry.getKey();
        if (key != null) {
          CachedState state = entry.getValue();
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

        CachedState state = new CachedState();
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
  public WindowState getStateFor(@Nullable Project project, @NotNull String key, @NotNull Window window) {
    synchronized (myRunnableMap) {
      WindowStateBean state = WindowStateAdapter.getState(window);
      Runnable runnable = myRunnableMap.put(key, new Runnable() {
        private long myModificationCount = state.getModificationCount();

        @Override
        public void run() {
          long newModificationCount = state.getModificationCount();
          if (myModificationCount != newModificationCount) {
            myModificationCount = newModificationCount;
            Point location = state.getLocation();
            Dimension size = state.getSize();
            putFor(project, key,
                   location, location != null,
                   size, size != null,
                   Frame.MAXIMIZED_BOTH == state.getExtendedState(), true,
                   state.isFullScreen(), true);
          }
        }
      });
      if (runnable != null) {
        runnable.run();
      }
    }
    return getFor(project, key, WindowState.class);
  }

  @Override
  public Point getLocationFor(Object object, @NotNull String key) {
    return getFor(object, key, Point.class);
  }

  @Override
  public void putLocationFor(Object object, @NotNull String key, Point location) {
    putFor(object, key, location, true, null, false, false, false, false, false);
  }

  @Override
  public Dimension getSizeFor(Object object, @NotNull String key) {
    return getFor(object, key, Dimension.class);
  }

  @Override
  public void putSizeFor(Object object, @NotNull String key, Dimension size) {
    putFor(object, key, null, false, size, true, false, false, false, false);
  }

  @Override
  public Rectangle getBoundsFor(Object object, @NotNull String key) {
    return getFor(object, key, Rectangle.class);
  }

  @Override
  public void putBoundsFor(Object object, @NotNull String key, Rectangle bounds) {
    Point location = apply(Rectangle::getLocation, bounds);
    Dimension size = apply(Rectangle::getSize, bounds);
    putFor(object, key, location, true, size, true, false, false, false, false);
  }

  private <T> T getFor(Object object, @NotNull String key, @NotNull Class<T> type) {
    if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) return null;
    if (Registry.is("ui.disable.dimension.service.keys")) return null;
    if (UISettings.getInstance().getPresentationMode()) key += ".inPresentationMode"; // separate key for the presentation mode
    GraphicsConfiguration configuration = getConfiguration(object);
    synchronized (myStateMap) {
      CachedState state = myStateMap.get(getAbsoluteKey(configuration, key));
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
    if (UISettings.getInstance().getPresentationMode()) key += ".inPresentationMode"; // separate key for the presentation mode
    GraphicsConfiguration configuration = getConfiguration(object);
    synchronized (myStateMap) {
      put(getAbsoluteKey(configuration, key), location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet);

      CachedState state = put(key, location, locationSet, size, sizeSet, maximized, maximizedSet, fullScreen, fullScreenSet);
      if (state != null) state.updateScreenRectangle(configuration); // update a screen to adjust stored state
    }
    myModificationCount.getAndIncrement();
  }

  @Nullable
  private CachedState put(@NotNull String key,
                          @Nullable Point location, boolean locationSet,
                          @Nullable Dimension size, boolean sizeSet,
                          boolean maximized, boolean maximizedSet,
                          boolean fullScreen, boolean fullScreenSet) {
    CachedState state = myStateMap.get(key);
    if (state == null) {
      state = new CachedState();
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
    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      Rectangle bounds = ScreenUtil.getScreenRectangle(device.getDefaultConfiguration());
      sb.append('/').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    if (configuration != null) {
      Rectangle bounds = ScreenUtil.getScreenRectangle(configuration);
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

  private static final class CachedState {
    private Rectangle myScreen;
    private Point myLocation;
    private Dimension mySize;
    private boolean myMaximized;
    private boolean myFullScreen;
    private long myTimeStamp;

    @SuppressWarnings("unchecked")
    <T> T get(@NotNull Class<T> type, @Nullable Rectangle screen) {
      Point location = apply(Point::new, myLocation);
      Dimension size = apply(Dimension::new, mySize);
      // convert location and size according to the given screen
      if (myScreen != null && screen != null && !screen.isEmpty()) {
        double w = myScreen.getWidth() / screen.getWidth();
        double h = myScreen.getHeight() / screen.getHeight();
        if (location != null) location.setLocation(screen.x + (location.x - myScreen.x) / w, screen.y + (location.y - myScreen.y) / h);
        if (size != null) size.setSize(size.width / w, size.height / h);
        if (!isVisible(location, size)) return null; // adjusted state is not visible
      }
      if (type == Point.class) return (T)location;
      if (type == Dimension.class) return (T)size;
      if (type == Rectangle.class) return location == null || size == null ? null : (T)new Rectangle(location, size);
      if (type != WindowState.class) throw new IllegalArgumentException();
      // copy a current state
      WindowStateBean state = new WindowStateBean();
      state.setLocation(location);
      state.setSize(size);
      state.setExtendedState(myMaximized ? Frame.MAXIMIZED_BOTH : Frame.NORMAL);
      state.setFullScreen(myFullScreen);
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

    void updateScreenRectangle(@Nullable GraphicsConfiguration configuration) {
      myScreen = myLocation == null
                 ? getScreenRectangle(configuration)
                 : mySize == null
                   ? ScreenUtil.getScreenRectangle(myLocation)
                   : ScreenUtil.getScreenRectangle(myLocation.x + mySize.width / 2, myLocation.y + mySize.height / 2);
    }
  }

  private static boolean isVisible(CachedState state) {
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
  private static <T, R> R apply(@NotNull Function<? super T, ? extends R> function, @Nullable T value) {
    return value == null ? null : function.apply(value);
  }
}
