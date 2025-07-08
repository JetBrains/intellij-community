// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.util.*;
import java.util.List;

public final class ScreenUtil {
  public static final String DISPOSE_TEMPORARY = "dispose.temporary";

  private static final @Nullable Map<@NotNull GraphicsConfiguration, @NotNull Pair<@NotNull Insets, @NotNull Long>> insetCache =
    Boolean.getBoolean("ide.cache.screen.insets") ? new WeakHashMap<>() : null;
  private static final int ourInsetsTimeout = SystemProperties.getIntProperty("ide.insets.cache.timeout", 5000);  // shouldn't be too long

  private ScreenUtil() { }

  public static boolean isVisible(@NotNull Point location) {
    return getScreenRectangle(location).contains(location);
  }

  public static boolean isVisible(@NotNull Rectangle bounds) {
    if (bounds.isEmpty()) return false;
    Rectangle[] allScreenBounds = getAllScreenBounds();
    for (Rectangle screenBounds : allScreenBounds) {
      final Rectangle intersection = screenBounds.intersection(bounds);
      if (intersection.isEmpty()) continue;
      final int sq1 = intersection.width * intersection.height;
      final int sq2 = bounds.width * bounds.height;
      double visibleFraction = (double)sq1 / (double)sq2;
      if (visibleFraction > 0.1) {
        return true;
      }
    }
    return false;
  }

  public static @NotNull Rectangle getMainScreenBounds() {
    return getScreenRectangle(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice());
  }

  private static @NotNull Rectangle @NotNull[] getAllScreenBounds() {
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    Rectangle[] result = new Rectangle[devices.length];
    for (int i = 0; i < devices.length; i++) {
      result[i] = getScreenRectangle(devices[i]);
    }
    return result;
  }

  public static @NotNull Shape getAllScreensShape() {
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    if (devices.length == 0) {
      return new Rectangle();
    }
    if (devices.length == 1) {
      return getScreenRectangle(devices[0]);
    }
    Area area = new Area();
    for (GraphicsDevice device : devices) {
      area.add(new Area(getScreenRectangle(device)));
    }
    return area;
  }

  /**
   * Returns the smallest rectangle that encloses a visible area of every screen.
   *
   * @return the smallest rectangle that encloses a visible area of every screen
   */
  public static @NotNull Rectangle getAllScreensRectangle() {
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    if (devices.length == 0) {
      return new Rectangle();
    }
    if (devices.length == 1) {
      return getScreenRectangle(devices[0]);
    }
    int minX = 0;
    int maxX = 0;
    int minY = 0;
    int maxY = 0;
    for (GraphicsDevice device : devices) {
      Rectangle rectangle = getScreenRectangle(device);
      int x = rectangle.x;
      if (minX > x) {
        minX = x;
      }
      x += rectangle.width;
      if (maxX < x) {
        maxX = x;
      }
      int y = rectangle.y;
      if (minY > y) {
        minY = y;
      }
      y += rectangle.height;
      if (maxY < y) {
        maxY = y;
      }
    }
    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }

  public static @NotNull Rectangle getScreenRectangle(@NotNull Point p) {
    return getScreenRectangle(p.x, p.y);
  }

  public static @NotNull Rectangle getScreenRectangle(@NotNull Component component) {
    GraphicsConfiguration configuration = component.getGraphicsConfiguration();
    if (configuration != null) return getScreenRectangle(configuration);
    // try to find the nearest screen if configuration is not available
    Point p = new Point();
    SwingUtilities.convertPointToScreen(p, component);
    return getScreenRectangle(p);
  }

  /**
   * @param bounds a rectangle used to find corresponding graphics device
   * @return a graphics device that contains the biggest part of the specified rectangle
   */
  public static @Nullable GraphicsDevice getScreenDevice(@NotNull Rectangle bounds) {
    GraphicsDevice candidate = null;
    int maxIntersection = 0;

    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      GraphicsConfiguration config = device.getDefaultConfiguration();
      final Rectangle rect = config.getBounds();
      Rectangle intersection = rect.intersection(bounds);
      if (intersection.isEmpty()) {
        continue;
      }
      if (intersection.width * intersection.height > maxIntersection) {
        maxIntersection = intersection.width * intersection.height;
        candidate = device;
      }
    }

    return candidate;
  }

  /**
   * Method removeNotify (and then addNotify) will be invoked for all components when main frame switches between states "Normal" <-> "FullScreen".
   * In this case we shouldn't call Disposer  in removeNotify and/or release some resources that we won't initialize again in addNotify (e.g. listeners).
   */
  public static boolean isStandardAddRemoveNotify(@Nullable Component component) {
    JRootPane rootPane = findMainRootPane(component);
    return rootPane == null || rootPane.getClientProperty(DISPOSE_TEMPORARY) == null;
  }

  private static @Nullable JRootPane findMainRootPane(@Nullable Component component) {
    while (component != null) {
      Container parent = component.getParent();
      if (parent == null) {
        return component instanceof RootPaneContainer ? ((RootPaneContainer)component).getRootPane() : null;
      }
      component = parent;
    }
    return null;
  }

  private static @NotNull Rectangle applyInsets(@NotNull Rectangle rect, @Nullable Insets i) {
    rect = new Rectangle(rect);
    JBInsets.removeFrom(rect, i);
    return rect;
  }

  public static @NotNull Insets getScreenInsets(final @NotNull GraphicsConfiguration gc) {
    if (insetCache == null) {
      return calcInsets(gc);
    }

    synchronized (insetCache) {
      Pair<Insets, Long> data = insetCache.get(gc);
      long now = System.currentTimeMillis();
      if (data == null || now > data.second + ourInsetsTimeout) {
        data = new Pair<>(calcInsets(gc), now);
        insetCache.put(gc, data);
      }
      return data.first;
    }
  }

  private static @NotNull Insets calcInsets(GraphicsConfiguration gc) {
    return Toolkit.getDefaultToolkit().getScreenInsets(gc);
  }

  /**
   * Returns a visible area for the specified graphics device.
   *
   * @param device one of available devices
   * @return a visible area rectangle
   */
  private static @NotNull Rectangle getScreenRectangle(@NotNull GraphicsDevice device) {
    return getScreenRectangle(device.getDefaultConfiguration());
  }

  /**
   * Returns a visible area for the specified graphics configuration.
   *
   * @param configuration one of available configurations
   * @return a visible area rectangle
   */
  public static @NotNull Rectangle getScreenRectangle(@NotNull GraphicsConfiguration configuration) {
    return applyInsets(configuration.getBounds(), getScreenInsets(configuration));
  }

  /**
   * Returns a visible area for a graphics device that is the closest to the specified point.
   *
   * @param x the X coordinate of the specified point
   * @param y the Y coordinate of the specified point
   * @return a visible area rectangle
   */
  public static @NotNull Rectangle getScreenRectangle(int x, int y) {
    if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance()) {
      return new Rectangle(x, y, 0, 0);
    }
    GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    if (devices.length == 0) {
      return new Rectangle(x, y, 0, 0);
    }
    if (devices.length == 1) {
      return getScreenRectangle(devices[0]);
    }
    Rectangle[] rectangles = new Rectangle[devices.length];
    for (int i = 0; i < devices.length; i++) {
      GraphicsConfiguration configuration = devices[i].getDefaultConfiguration();
      Rectangle bounds = configuration.getBounds();
      rectangles[i] = applyInsets(bounds, getScreenInsets(configuration));
      if (bounds.contains(x, y)) {
        return rectangles[i];
      }
    }
    Rectangle bounds = rectangles[0];
    int minimum = distance(bounds, x, y);
    if (bounds.width == 0 || bounds.height == 0) {
      //Screen is invalid, give maximum score
      minimum = Integer.MAX_VALUE;
    }
    for (int i = 1; i < rectangles.length; i++) {
      if (rectangles[i].width == 0 || rectangles[i].height == 0) {
        //Screen is invalid
        continue;
      }
      int distance = distance(rectangles[i], x, y);
      if (minimum > distance) {
        minimum = distance;
        bounds = rectangles[i];
      }
    }
    if (bounds.width == 0 || bounds.height == 0) {
      //All screens were invalid, return sensible default
      return new Rectangle(x, y, 0, 0);
    }
    return bounds;
  }

  /**
   * Normalizes a specified value in the specified range.
   * If value less than the minimal value,
   * the method returns the minimal value.
   * If value greater than the maximal value,
   * the method returns the maximal value.
   *
   * @param value the value to normalize
   * @param min   the minimal value of the range
   * @param max   the maximal value of the range
   * @return a normalized value
   */
  private static int normalize(int value, int min, int max) {
    return value < min ? min : Math.min(value, max);
  }

  /**
   * Returns a square of the distance from
   * the specified point to the specified rectangle,
   * which does not contain the specified point.
   *
   * @param x the X coordinate of the specified point
   * @param y the Y coordinate of the specified point
   * @return a square of the distance
   */
  private static int distance(@NotNull Rectangle bounds, int x, int y) {
    x -= normalize(x, bounds.x, bounds.x + bounds.width);
    y -= normalize(y, bounds.y, bounds.y + bounds.height);
    return x * x + y * y;
  }

  public static void moveAndScale(@NotNull Point location, @NotNull Rectangle fromScreen, @NotNull Rectangle toScreen) {
    checkScreensNonEmpty(fromScreen, toScreen);
    double kw = toScreen.getWidth() / fromScreen.getWidth();
    double kh = toScreen.getHeight() / fromScreen.getHeight();
    location.setLocation(toScreen.x + (location.x - fromScreen.x) * kw, toScreen.y + (location.y - fromScreen.y) * kh);
  }

  public static void moveAndScale(@NotNull Dimension size, @NotNull Rectangle fromScreen, @NotNull Rectangle toScreen) {
    checkScreensNonEmpty(fromScreen, toScreen);
    double kw = toScreen.getWidth() / fromScreen.getWidth();
    double kh = toScreen.getHeight() / fromScreen.getHeight();
    size.setSize(size.width * kw, size.height * kh);
  }

  public static void moveAndScale(@NotNull Rectangle bounds, @NotNull Rectangle fromScreen, @NotNull Rectangle toScreen) {
    checkScreensNonEmpty(fromScreen, toScreen);
    double kw = toScreen.getWidth() / fromScreen.getWidth();
    double kh = toScreen.getHeight() / fromScreen.getHeight();
    bounds.setRect(
      toScreen.x + (bounds.x - fromScreen.x) * kw,
      toScreen.y + (bounds.y - fromScreen.y) * kh,
      bounds.width * kw,
      bounds.height * kh
    );
  }

  private static void checkScreensNonEmpty(@NotNull Rectangle fromScreen, @NotNull Rectangle toScreen) {
    if (fromScreen.isEmpty()) {
      throw new IllegalArgumentException("Can't move from an empty screen: " + fromScreen);
    }
    if (toScreen.isEmpty()) {
      throw new IllegalArgumentException("Can't move to an empty screen: " + toScreen);
    }
  }

  public static void moveRectangleToFitTheScreen(@NotNull Rectangle aRectangle) {
    if (StartupUiUtil.isWaylandToolkit()) return; // No abs coordinates in Wayland

    int screenX = aRectangle.x + aRectangle.width / 2;
    int screenY = aRectangle.y + aRectangle.height / 2;
    Rectangle screen = getScreenRectangle(screenX, screenY);

    moveToFit(aRectangle, screen, null);
  }

  public static void moveToFit(final @NotNull Rectangle rectangle, final @NotNull Rectangle container, @Nullable Insets padding) {
    moveToFit(rectangle, container, padding, false);
  }

  public static void moveToFit(final @NotNull Rectangle rectangle, final @NotNull Rectangle container, @Nullable Insets padding, boolean crop) {
    Rectangle move = new Rectangle(rectangle);
    JBInsets.addTo(move, padding);

    if (move.getMaxX() > container.getMaxX()) {
      move.x = (int)container.getMaxX() - move.width;
    }
    if (move.getMinX() < container.getMinX()) {
      move.x = (int)container.getMinX();
    }
    if (move.getMaxX() > container.getMaxX() && crop) {
      move.width = (int)container.getMaxX() - move.x;
    }

    if (move.getMaxY() > container.getMaxY()) {
      move.y = (int)container.getMaxY() - move.height;
    }
    if (move.getMinY() < container.getMinY()) {
      move.y = (int)container.getMinY();
    }
    if (move.getMaxY() > container.getMaxY() && crop) {
      move.height = (int)container.getMaxY() - move.y;
    }

    JBInsets.removeFrom(move, padding);
    rectangle.setBounds(move);
  }

  /**
   * Finds the best place for the specified rectangle on the screen.
   *
   * @param rectangle    the rectangle to move and resize
   * @param top          preferred offset between {@code rectangle.y} and popup above
   * @param bottom       preferred offset between {@code rectangle.y} and popup below
   * @param rightAligned shows that the rectangle should be moved to the left
   */
  public static void fitToScreenVertical(@NotNull Rectangle rectangle, int top, int bottom, boolean rightAligned) {
    if (StartupUiUtil.isWaylandToolkit()) return; // No abs coordinates in Wayland

    Rectangle screen = getScreenRectangle(rectangle.x, rectangle.y);
    if (rectangle.width > screen.width) {
      rectangle.width = screen.width;
    }
    if (rightAligned) {
      rectangle.x -= rectangle.width;
    }
    if (rectangle.x < screen.x) {
      rectangle.x = screen.x;
    }
    else {
      int max = screen.x + screen.width;
      if (rectangle.x > max) {
        rectangle.x = max - rectangle.width;
      }
    }
    int above = rectangle.y - screen.y - top;
    int below = screen.height - above - top - bottom;
    if (below > rectangle.height) {
      rectangle.y += bottom;
    }
    else if (above > rectangle.height) {
      rectangle.y -= rectangle.height + top;
    }
    else if (below > above) {
      rectangle.y += bottom;
      rectangle.height = below;
    }
    else {
      rectangle.y -= rectangle.height + top;
      rectangle.height = above;
    }
  }

  public static void fitToScreen(@NotNull Rectangle r) {
    if (StartupUiUtil.isWaylandToolkit()) return; // No abs coordinates in Wayland

    Rectangle screen = getScreenRectangle(r.x, r.y);

    int xOverdraft = r.x + r.width - screen.x - screen.width;
    if (xOverdraft > 0) {
      int shift = Math.min(xOverdraft, r.x - screen.x);
      xOverdraft -= shift;
      r.x -= shift;
      if (xOverdraft > 0) {
        r.width -= xOverdraft;
      }
    }

    int yOverdraft = r.y + r.height - screen.y - screen.height;
    if (yOverdraft > 0) {
      int shift = Math.min(yOverdraft, r.y - screen.y);
      yOverdraft -= shift;
      r.y -= shift;
      if (yOverdraft > 0) {
        r.height -= yOverdraft;
      }
    }
  }

  public static @NotNull Point findNearestPointOnBorder(@NotNull Rectangle rect, @NotNull Point p) {
    final int x0 = rect.x;
    final int y0 = rect.y;
    final int x1 = x0 + rect.width;
    final int y1 = y0 + rect.height;
    double distance = -1;
    Point best = null;
    final Point[] variants = {new Point(p.x, y0), new Point(p.x, y1), new Point(x0, p.y), new Point(x1, p.y)};
    for (Point variant : variants) {
      final double d = variant.distance(p.x, p.y);
      if (best == null || distance > d) {
        best = variant;
        distance = d;
      }
    }
    return best;
  }

  public static void cropRectangleToFitTheScreen(@NotNull Rectangle rect) {
    if (StartupUiUtil.isWaylandToolkit()) return; // No abs coordinates in Wayland

    int screenX = rect.x;
    int screenY = rect.y;
    final Rectangle screen = getScreenRectangle(screenX, screenY);

    if (rect.getMaxX() > screen.getMaxX()) {
      rect.width = (int)screen.getMaxX() - rect.x;
    }

    if (rect.getMinX() < screen.getMinX()) {
      rect.x = (int)screen.getMinX();
    }

    if (rect.getMaxY() > screen.getMaxY()) {
      rect.height = (int)screen.getMaxY() - rect.y;
    }

    if (rect.getMinY() < screen.getMinY()) {
      rect.y = (int)screen.getMinY();
    }
  }

  /**
   *
   * @param prevLocation - previous location on screen
   * @param location - current location on screen
   * @param bounds - area to check if location shifted towards or not. Also in screen coordinates
   * @return true if movement from prevLocation to location is towards specified rectangular area
   */
  public static boolean isMovementTowards(final @Nullable Point prevLocation, @NotNull Point location, final @Nullable Rectangle bounds) {
    if (bounds == null) {
      return false;
    }
    if (prevLocation == null || prevLocation.equals(location)) {
      return true;
    }
    // consider any movement inside a rectangle as a valid movement towards
    if (bounds.contains(location)) {
      return true;
    }

    int dx = prevLocation.x - location.x;
    int dy = prevLocation.y - location.y;

    // Check if the mouse goes out of the control.
    if (dx > 0 && bounds.x >= prevLocation.x) return false;
    if (dx < 0 && bounds.x + bounds.width <= prevLocation.x) return false;
    if (dy < 0 && bounds.y + bounds.height <= prevLocation.y) return false;
    if (dy > 0 && bounds.y >= prevLocation.y) return false;
    if (dx == 0) {
      return (location.x >= bounds.x && location.x < bounds.x + bounds.width)
             && (dy > 0 ^ bounds.y > location.y);
    }
    if (dy == 0) {
      return (location.y >= bounds.y && location.y < bounds.y + bounds.height)
             && (dx > 0 ^ bounds.x > location.x);
    }


    // Calculate line equation parameters - y = a * x + b
    float a = (float)dy / dx;
    float b = location.y - a * location.x;

    // Check if crossing point with any tooltip border line is within bounds. Don't bother with floating point inaccuracy here.

    // Left border.
    float crossY = a * bounds.x + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;

    // Right border.
    crossY = a * (bounds.x + bounds.width) + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;

    // Top border.
    float crossX = (bounds.y - b) / a;
    if (crossX >= bounds.x && crossX < bounds.x + bounds.width) return true;

    // Bottom border
    crossX = (bounds.y + bounds.height - b) / a;
    if (crossX >= bounds.x && crossX < bounds.x + bounds.width) return true;

    return false;
  }

  public static boolean intersectsVisibleScreen(@NotNull Window window) {
    if (StartupUiUtil.isWaylandToolkit()) return true; // No window coordinates in Wayland
    GraphicsConfiguration configuration = window.getGraphicsConfiguration();
    // keep the ID in sync with com.intellij.platform.impl.toolkit.HeadlessDummyGraphicsEnvironment, can't be referenced here
    if (Objects.equals(configuration.getDevice().getIDstring(), "DummyHeadless")) return true;
    return configuration.getBounds().intersects(window.getBounds());
  }

  /**
   * Logs the current monitor configuration.
   *
   * @param ideFrame if not null, then the monitor containing this window will be marked "IDE frame" in the message
   */
  @ApiStatus.Internal
  public static @NotNull List<@NotNull String> loggableMonitorConfiguration(@Nullable JFrame ideFrame) {
    var result = new ArrayList<@NotNull String>();
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    for (GraphicsDevice device : ge.getScreenDevices()) {
      DisplayMode displayMode = device.getDisplayMode();
      GraphicsConfiguration gc = device.getDefaultConfiguration();
      float scale = JBUIScale.sysScale(gc);
      Rectangle bounds = getScreenRectangle(gc);
      result.add(String.format("%s (%dx%d scaled at %.02f with insets %s)%s%s",
                              bounds, displayMode.getWidth(), displayMode.getHeight(), scale, getScreenInsets(gc),
                              (device == ge.getDefaultScreenDevice() ? ", default" : ""),
                              (ideFrame != null && device == ideFrame.getGraphicsConfiguration().getDevice() ? ", IDE frame" : "")
      ));
    }
    return result;
  }
}
