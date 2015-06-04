/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.Patches;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.ui.FrameState;
import com.intellij.ui.ScreenUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Sergey.Malenkov
 */
abstract class WindowStateServiceImpl extends WindowStateService implements PersistentStateComponent<Element> {
  @NonNls private static final String KEY = "key";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String X = "x";
  @NonNls private static final String Y = "y";
  @NonNls private static final String WIDTH = "width";
  @NonNls private static final String HEIGHT = "height";
  @NonNls private static final String EXTENDED = "extended-state";

  private final Map<String, WindowState> myStateMap = new TreeMap<String, WindowState>();

  abstract Point getDefaultLocationOn(GraphicsDevice screen, @NotNull String key);

  abstract Dimension getDefaultSizeOn(GraphicsDevice screen, @NotNull String key);

  abstract Rectangle getDefaultBoundsOn(GraphicsDevice screen, @NotNull String key);

  abstract Integer getDefaultExtendedStateOn(GraphicsDevice screen, @NotNull String key);

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
            child.setAttribute(X, Integer.toString(state.myLocation.x));
            child.setAttribute(Y, Integer.toString(state.myLocation.y));
          }
          if (state.mySize != null) {
            child.setAttribute(WIDTH, Integer.toString(state.mySize.width));
            child.setAttribute(HEIGHT, Integer.toString(state.mySize.height));
          }
          if (state.myState != null) {
            child.setAttribute(EXTENDED, state.myState.toString());
          }
          child.setAttribute(KEY, key);
          element.addContent(child);
        }
      }
    }
    return element;
  }

  @Override
  public final void loadState(Element element) {
    synchronized (myStateMap) {
      myStateMap.clear();
      for (Element child : element.getChildren()) {
        if (STATE.equals(child.getName())) {
          String key = child.getAttributeValue(KEY);
          if (key != null) {
            WindowState state = new WindowState();
            try {
              state.myLocation = new Point(
                Integer.parseInt(child.getAttributeValue(X)),
                Integer.parseInt(child.getAttributeValue(Y)));
            }
            catch (NumberFormatException ignored) {
            }
            try {
              state.mySize = new Dimension(
                Integer.parseInt(child.getAttributeValue(WIDTH)),
                Integer.parseInt(child.getAttributeValue(HEIGHT)));
            }
            catch (NumberFormatException ignored) {
            }
            try {
              state.myState = Integer.valueOf(child.getAttributeValue(EXTENDED));
            }
            catch (NumberFormatException ignored) {
            }
            if (!state.isEmpty()) {
              myStateMap.put(key, state);
            }
          }
        }
      }
    }
  }

  @Override
  public boolean loadStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
    Point location = null;
    Dimension size = null;
    Integer extendedState = null;
    synchronized (myStateMap) {
      WindowState state = myStateMap.get(getKey(screen, key));
      boolean visible = isVisible(state);
      if (!visible) {
        state = myStateMap.get(key);
        visible = isVisible(state);
      }
      if (visible) {
        location = state.myLocation;
        size = state.mySize;
        extendedState = state.myState;
      }
    }
    if (location == null && size == null) {
      location = getDefaultLocationOn(screen, key);
      size = getDefaultSizeOn(screen, key);
      if (!isVisible(location, size)) {
        return false;
      }
      extendedState = getDefaultExtendedStateOn(screen, key);
    }
    Frame frame = component instanceof Frame ? (Frame)component : null;
    if (frame != null) {
      frame.setExtendedState(Frame.NORMAL);
    }
    Rectangle bounds = component.getBounds();
    if (location != null) {
      bounds.setLocation(location);
    }
    if (size != null) {
      bounds.setSize(size);
    }
    component.setBounds(bounds);
    if (!Patches.JDK_BUG_ID_8007219 && frame != null && FrameState.isMaximized(extendedState)) {
      frame.setExtendedState(Frame.MAXIMIZED_BOTH);
    }
    return true;
  }

  @Override
  public void saveStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
    FrameState state = FrameState.getFrameState(component);
    putOn(screen, key, state.getLocation(), true, state.getSize(), true, state.getExtendedState(), true);
  }

  private Point getVisibleLocation(@NotNull String key) {
    synchronized (myStateMap) {
      WindowState state = myStateMap.get(key);
      return !isVisible(state) ? null : state.getLocation();
    }
  }

  @Override
  public Point getLocationOn(GraphicsDevice screen, @NotNull String key) {
    Point location = getVisibleLocation(getKey(screen, key));
    if (location == null) {
      location = getVisibleLocation(key);
      if (location == null) {
        location = getDefaultLocationOn(screen, key);
      }
    }
    return location;
  }

  @Override
  public void putLocationOn(GraphicsDevice screen, @NotNull String key, Point location) {
    putOn(screen, key, location, true, null, false, null, false);
  }

  private Dimension getVisibleSize(@NotNull String key) {
    synchronized (myStateMap) {
      WindowState state = myStateMap.get(key);
      return !isVisible(state) ? null : state.getSize();
    }
  }

  @Override
  public Dimension getSizeOn(GraphicsDevice screen, @NotNull String key) {
    Dimension size = getVisibleSize(getKey(screen, key));
    if (size == null) {
      size = getVisibleSize(key);
      if (size == null) {
        size = getDefaultSizeOn(screen, key);
      }
    }
    return size;
  }

  @Override
  public void putSizeOn(GraphicsDevice screen, @NotNull String key, Dimension size) {
    putOn(screen, key, null, false, size, true, null, false);
  }

  private Rectangle getVisibleBounds(@NotNull String key) {
    synchronized (myStateMap) {
      WindowState state = myStateMap.get(key);
      return !isVisible(state) ? null : state.getBounds();
    }
  }

  @Override
  public Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key) {
    Rectangle bounds = getVisibleBounds(getKey(screen, key));
    if (bounds == null) {
      bounds = getVisibleBounds(key);
      if (bounds == null) {
        bounds = getDefaultBoundsOn(screen, key);
      }
    }
    return bounds;
  }

  @Override
  public void putBoundsOn(GraphicsDevice screen, @NotNull String key, Rectangle bounds) {
    Point location = bounds == null ? null : bounds.getLocation();
    Dimension size = bounds == null ? null : bounds.getSize();
    putOn(screen, key, location, true, size, true, null, false);
  }

  private void putOn(GraphicsDevice screen, @NotNull String key,
                     Point location, boolean locationSet,
                     Dimension size, boolean sizeSet,
                     Integer extendedState, boolean extendedStateSet) {
    synchronized (myStateMap) {
      putImpl(getKey(screen, key), location, locationSet, size, sizeSet, extendedState, extendedStateSet);
      putImpl(key, location, locationSet, size, sizeSet, extendedState, extendedStateSet);
    }
  }

  private void putImpl(@NotNull String key,
                       Point location, boolean locationSet,
                       Dimension size, boolean sizeSet,
                       Integer extendedState, boolean extendedStateSet) {
    WindowState state = myStateMap.get(key);
    if (state != null) {
      state.set(location, locationSet, size, sizeSet, extendedState, extendedStateSet);
      if (state.isEmpty()) {
        myStateMap.remove(key);
      }
    }
    else {
      state = new WindowState();
      state.set(location, locationSet, size, sizeSet, extendedState, extendedStateSet);
      if (!state.isEmpty()) {
        myStateMap.put(key, state);
      }
    }
  }

  @NotNull
  private static String getKey(GraphicsDevice screen, String key) {
    GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    if (environment.isHeadlessInstance()) {
      return key + ".headless";
    }
    StringBuilder sb = new StringBuilder(key);
    for (GraphicsDevice device : environment.getScreenDevices()) {
      Rectangle bounds = device.getDefaultConfiguration().getBounds();
      sb.append('/').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    if (screen != null) {
      Rectangle bounds = screen.getDefaultConfiguration().getBounds();
      sb.append('@').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    return sb.toString();
  }

  private static final class WindowState {
    private Point myLocation;
    private Dimension mySize;
    private Integer myState;

    private Point getLocation() {
      return myLocation == null ? null : new Point(myLocation);
    }

    private Dimension getSize() {
      return mySize == null ? null : new Dimension(mySize);
    }

    private Rectangle getBounds() {
      return myLocation == null || mySize == null ? null : new Rectangle(myLocation, mySize);
    }

    private void set(Point location, boolean locationSet, Dimension size, boolean sizeSet, Integer state, boolean stateSet) {
      if (locationSet) {
        myLocation = location == null ? null : new Point(location);
      }
      if (sizeSet) {
        mySize = size == null ? null : new Dimension(size);
      }
      if (stateSet) {
        myState = state;
      }
    }

    private boolean isEmpty() {
      return myLocation == null && mySize == null && myState == null;
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
}
