// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.system.OS;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ComponentUtil {
  private static final @NonNls String FOCUS_PROXY_KEY = "isFocusProxy";
  private static final Key<Boolean> IS_SHOWING = Key.create("Component.isShowing");
  @ApiStatus.Internal
  public static final Key<Iterable<? extends Component>> NOT_IN_HIERARCHY_COMPONENTS = Key.create("NOT_IN_HIERARCHY_COMPONENTS");

  private ComponentUtil() {}

  /**
   * @deprecated use {@link ClientProperty#get(Component, Key)} instead
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public static <T> T getClientProperty(@NotNull JComponent component, @NotNull Key<T> key) {
    return ClientProperty.get(component, key);
  }

  /**
   * @deprecated use {@link JComponent#putClientProperty(Object, Object)} or {@link ClientProperty#put(JComponent, Key, Object)} instead
   */
  @Deprecated
  public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
    component.putClientProperty(key, value);
  }

  public static boolean isMinimized(@Nullable Window window) {
    if (!(window instanceof Frame frame)) {
      return false;
    }

    return frame.getExtendedState() == Frame.ICONIFIED;
  }

  public static @NotNull Window getActiveWindow() {
    for (Window each : Window.getWindows()) {
      if (each.isVisible() && each.isActive()) {
        return each;
      }
    }
    return JOptionPane.getRootFrame();
  }

  public static @NotNull Component findUltimateParent(@NotNull Component c) {
    Component parent = c;
    while (true) {
      Container nextParent = parent.getParent();
      if (nextParent == null) {
        return parent;
      }
      parent = nextParent;
    }
  }

  /**
   * Returns the first window ancestor of the component,
   * or {@code null} the component is not a window and is not contained inside a window.
   * Note that this method returns the component itself if it is a window.
   */
  public static @Nullable Window getWindow(@Nullable Component component) {
    return component == null ? null :
           component instanceof Window ? (Window)component :
           SwingUtilities.getWindowAncestor(component);
  }

  public static @Nullable Component findParentByCondition(@Nullable Component c, @NotNull Predicate<? super Component> condition) {
    Component eachParent = c;
    while (eachParent != null) {
      if (condition.test(eachParent)) return eachParent;
      eachParent = eachParent.getParent();
    }
    return null;
  }

  /**
   * Searches above in the component hierarchy starting from the specified component.
   * Note that the initial component is <b>also checked</b>.
   *
   * @param type      expected class
   * @param component initial component
   * @return a component of the specified type, or {@code null} if the search is failed
   * @see #getStrictParentOfType(Class, Component)
   */
  @Contract(pure = true)
  public static @Nullable <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
    while (component != null) {
      if (type.isInstance(component)) {
        //noinspection unchecked
        return (T)component;
      }
      component = component.getParent();
    }
    return null;
  }

  /**
   * Searches above in the component hierarchy starting from the specified component.
   * Note that the initial component is <b>not checked</b>.
   *
   * @param type      expected class
   * @param component initial component
   * @return a component of the specified type, or {@code null} if the search is failed
   * @see #getParentOfType(Class, Component)
   */
  @Contract(pure = true)
  public static @Nullable <T> T getStrictParentOfType(@NotNull Class<? extends T> type, Component component) {
    while (component != null) {
      component = component.getParent();
      if (type.isInstance(component)) {
        //noinspection unchecked
        return (T)component;
      }
    }
    return null;
  }

  /**
   * @param component a view component of the requested scroll pane
   * @return a scroll pane for the given component or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable Component component) {
    return component instanceof JScrollBar
           ? getScrollPane((JScrollBar)component)
           : getScrollPane(component instanceof JViewport
                           ? (JViewport)component
                           : getViewport(component));
  }

  /**
   * @param bar a scroll bar of the requested scroll pane
   * @return a scroll pane for the given scroll bar or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable JScrollBar bar) {
    Container parent = bar == null ? null : bar.getParent();
    return parent instanceof JScrollPane ? (JScrollPane)parent : null;
  }

  /**
   * @param viewport a viewport of the requested scroll pane
   * @return a scroll pane for the given viewport or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable JViewport viewport) {
    Container parent = viewport == null ? null : viewport.getParent();
    return parent instanceof JScrollPane ? (JScrollPane)parent : null;
  }

  /**
   * @param component a view component of the requested viewport
   * @return a viewport for the given component or {@code null} if none
   */
  public static @Nullable JViewport getViewport(@Nullable Component component) {
    Container parent = component == null ? null : SwingUtilities.getUnwrappedParent(component);
    return parent instanceof JViewport ? (JViewport)parent : null;
  }

  public static @NotNull <T extends JComponent> java.util.List<T> findComponentsOfType(@Nullable JComponent parent, @NotNull Class<? extends T> cls) {
    java.util.List<T> result = new ArrayList<>();
    findComponentsOfType(parent, cls, result);
    return result;
  }

  /**
   * A potentially faster version of {@link SwingUtilities#convertRectangle(Component, Rectangle, Component)}
   * <p>
   *   See {@link #convertPoint(Component, Point, Component)} for an explanation.
   * </p>
   * @param source the source component, relative to which the {@code rectangle} is given
   * @param rectangle the source rectangle
   * @param destination the destination component, to which to convert
   * @return a new instance of {@code Rectangle}, relative to {@code destination}
   */
  public static @NotNull Rectangle convertRectangle(
    @NotNull Component source,
    @NotNull Rectangle rectangle,
    @NotNull Component destination
  ) {
    var point = new Point(rectangle.x, rectangle.y);
    point =  convertPoint(source, point, destination);
    return new Rectangle(point.x, point.y, rectangle.width, rectangle.height);
  }

  /**
   * A potentially faster version of {@link SwingUtilities#convertPoint(Component, Point, Component)}
   * <p>
   *   Doesn't call {@link Component#getLocationOnScreen()} when {@code source} is in {@code destination}'s hierarchy or vice versa.
   *   This means avoiding potentially expensive native calls, as everything can be computed very quickly relying only on the data that is already here.
   * </p>
   * @param source the source component, relative to which the {@code point} is given
   * @param point the source point
   * @param destination the destination component, to which to convert
   * @return a new instance of {@code Point}, relative to {@code destination}
   */
  public static @NotNull Point convertPoint(@NotNull Component source, @NotNull Point point, @NotNull Component destination) {
    // It doesn't matter which component we start from,
    // but converting to an ancestor is a far more common case:
    // e.g., converting several components to a common parent to compare their coordinates in one system.
    // So we start going up from the source for the most fast-path scenario.
    var result = new Point(point);
    var currentSource = source;
    // invariant: at the end of each iteration, result is in the currentSource's coordinate system
    while (currentSource != destination) {
      var sourceParent = currentSource.getParent();
      if (sourceParent == null || sourceParent instanceof Window) break; // Windows are not positioned relative to each other
      var sourceLocation = currentSource.getLocation();
      result.x += sourceLocation.x;
      result.y += sourceLocation.y;
      currentSource = sourceParent;
    }
    if (currentSource == destination) return result; // the fast-path case: converting to an ancestor

    // We've reached the ultimate parent (currentSource).
    // Now we have the point relative to it.
    // But we still have no clue about where our destination is,
    // so let's start figuring that out.
    var currentDestination = destination;
    var destinationRelativeToCurrentDestinationX = 0;
    var destinationRelativeToCurrentDestinationY = 0;
    while (currentDestination != currentSource) {
      var destinationParent = currentDestination.getParent();
      if (destinationParent == null || destinationParent instanceof Window) break;
      destinationRelativeToCurrentDestinationX += currentDestination.getX();
      destinationRelativeToCurrentDestinationY += currentDestination.getY();
      currentDestination = destinationParent;
    }

    // If the ultimate parents are the same, and we have the answer relative to currentSource,
    // it means we now have the result relative to currentDestination (as it's the same).
    // If they're not the same, delegate to SwingUtilities.convertPoint to convert between them.
    if (currentDestination != currentSource) {
      // We could've done everything instead of delegating,
      // but there are some tricks inside that handle the showing / not showing cases,
      // so the safest bet is to delegate, because at this point no meaningful optimization is possible anyway.
      var difference = SwingUtilities.convertPoint(currentSource, 0, 0, currentDestination);
      result.x += difference.x;
      result.y += difference.y;
    }

    // Now we have the result relative to currentDestination.
    // And we already know where it is relative to the original destination.
    result.x -= destinationRelativeToCurrentDestinationX;
    result.y -= destinationRelativeToCurrentDestinationY;
    return result;
  }

  private static <T extends JComponent> void findComponentsOfType(@Nullable JComponent parent,
                                                                  @NotNull Class<T> cls,
                                                                  @NotNull List<? super T> result) {
    if (parent == null) return;
    if (cls.isAssignableFrom(parent.getClass())) {
      @SuppressWarnings("unchecked") final T t = (T)parent;
      result.add(t);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType((JComponent)c, cls, result);
      }
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static boolean isMeaninglessFocusOwner(@Nullable Component c) {
    if (c == null || !c.isShowing()) {
      return true;
    }
    return c instanceof JFrame || c instanceof JDialog || c instanceof JWindow || c instanceof JRootPane || isFocusProxy(c);
  }

  /**
   * An overload of {@link UIUtil#isShowing(Component)} allowing to ignore headless mode.
   *
   * @param checkHeadless when {@code true}, the {@code component} will always be considered visible in headless mode.
   */
  @ApiStatus.Experimental
  public static boolean isShowing(@NotNull Component component, boolean checkHeadless) {
    if (checkHeadless && Boolean.getBoolean("java.awt.headless")) {
      return true;
    }
    if (component.isShowing()) {
      return true;
    }

    while (component != null) {
      JComponent jComponent = component instanceof JComponent ? (JComponent)component : null;
      if (jComponent != null && Boolean.TRUE.equals(jComponent.getClientProperty(IS_SHOWING))) {
        return true;
      }
      component = component.getParent();
    }

    return false;
  }

  /**
   * Marks a component as showing
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void markAsShowing(@NotNull JComponent component, boolean value) {
    if (Boolean.getBoolean("java.awt.headless")) {
      return;
    }
    forceMarkAsShowing(component, value);
  }

  /**
   * Marks a component as showing regardless of headless mode.
   *
   * @see #isShowing(Component, boolean)
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static void forceMarkAsShowing(@NotNull JComponent component, boolean value) {
    component.putClientProperty(IS_SHOWING, value ? Boolean.TRUE : null);
  }

  public static void decorateWindowHeader(@Nullable JRootPane pane) {
    if (pane != null && OS.CURRENT == OS.macOS) {
      pane.putClientProperty(
        "apple.awt.windowAppearance",
        StartupUiUtil.INSTANCE.isDarkTheme() ? "NSAppearanceNameVibrantDark" : "NSAppearanceNameVibrantLight"
      );
    }
  }
}
