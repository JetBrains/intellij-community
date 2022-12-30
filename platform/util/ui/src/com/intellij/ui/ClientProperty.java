// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientProperty {
  /**
   * Sets the value for the client property of the component.
   * This is a convenient way to specify a value as a lambda.
   *
   * @param component a Swing component that may hold a client property value
   * @param key       a typed key corresponding to a client property
   * @param value     new value for the client property
   * @see JComponent#putClientProperty(Object, Object)
   */
  public static <T> void put(@NotNull JComponent component, @NotNull Key<T> key, @Nullable T value) {
    component.putClientProperty(key, value);
  }

  /**
   * Sets the value for the client property of the window.
   * This is a convenient way to specify a value as a lambda.
   *
   * @param window a Swing window that may hold a client property value
   * @param key    a typed key corresponding to a client property
   * @param value  new value for the client property
   * @return {@code true} if property is set to the corresponding root pane, {@code false} otherwise
   */
  public static <T> boolean put(@NotNull Window window, @NotNull Key<T> key, @Nullable T value) {
    return put(window, (Object)key, value);
  }

  public static <T> void remove(@NotNull JComponent component, @NotNull Key<T> key) {
    put(component, key, null);
  }

  public static <T> void remove(@NotNull Window window, @NotNull Key<T> key) {
    put(window, key, null);
  }

  /**
   * Sets the value for the client property of the window.
   * All these properties will be put into the corresponding root pane.
   *
   * @param window a Swing window that may hold a client property value
   * @param key    a key corresponding to a client property
   * @param value  new value for the client property
   * @return {@code true} if property is set to the corresponding root pane, {@code false} otherwise
   */
  @Contract("null,_,_ -> false")
  public static boolean put(@Nullable Window window, @NotNull @NonNls Object key, @Nullable Object value) {
    JComponent holder = getPropertiesHolder(window);
    if (holder != null) holder.putClientProperty(key, value);
    return holder != null;
  }

  @Contract("null -> null")
  private static @Nullable JComponent getPropertiesHolder(@Nullable Component component) {
    if (component instanceof JComponent) return (JComponent)component;
    if (component instanceof Window && component instanceof RootPaneContainer container) {
      // store window properties in its root pane
      return container.getRootPane();
    }
    return null;
  }


  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @return the property value from the specified component or {@code null}
   */
  @Contract("null,_ -> null")
  public static @Nullable Object get(@Nullable Component component, @NotNull @NonNls Object key) {
    JComponent holder = getPropertiesHolder(component);
    return holder == null ? null : holder.getClientProperty(key);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @return the property value from the specified component or {@code null}
   */
  @Contract("null,_ -> null")
  public static @Nullable Object findInHierarchy(@Nullable Component component, @NotNull @NonNls Object key) {
    while (component != null) {
      Object value = get(component, key);
      if (value != null) return value;
      if (component instanceof Window) break;
      component = component.getParent();
    }
    return null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a typed key corresponding to a client property
   * @return the property value from the specified component or {@code null}
   */
  @SuppressWarnings("unchecked")
  @Contract("null,_ -> null")
  public static <T> @Nullable T get(@Nullable Component component, @NotNull Key<T> key) {
    Object value = get(component, (Object)key);
    return value != null ? (T)value : null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a typed key corresponding to a client property
   * @return the property value from the specified component or {@code null}
   */
  @SuppressWarnings("unchecked")
  @Contract("null,_ -> null")
  public static <T> @Nullable T findInHierarchy(@Nullable Component component, @NotNull Key<T> key) {
    Object value = findInHierarchy(component, (Object)key);
    return value != null ? (T)value : null;
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @return {@code true} if the property value is not null, or {@code false} otherwise
   */
  public static boolean isSet(@Nullable Component component, @NotNull @NonNls Object key) {
    return null != get(component, key);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @return {@code true} if the property value is not null, or {@code false} otherwise
   */
  public static boolean isSetInHierarchy(@Nullable Component component, @NotNull @NonNls Object key) {
    return null != findInHierarchy(component, key);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @param value     the expected value
   * @return {@code true} if the property value is equal to the {@code expected}, or {@code false} otherwise
   */
  public static boolean isSet(@Nullable Component component, @NotNull @NonNls Object key, @NotNull Object value) {
    return value.equals(get(component, key));
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a client property
   * @param value     the expected value
   * @return {@code true} if the property value is equal to the {@code expected}, or {@code false} otherwise
   */
  public static boolean isSetInHierarchy(@Nullable Component component, @NotNull @NonNls Object key, @NotNull Object value) {
    return value.equals(findInHierarchy(component, key));
  }


  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a boolean client property
   * @return {@code true} if the property value is {@link Boolean#TRUE}, or {@code false} otherwise
   */
  public static boolean isTrue(@Nullable Component component, @NotNull Object key) {
    return isSet(component, key, Boolean.TRUE);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a boolean client property
   * @return {@code true} if the property value is {@link Boolean#TRUE}, or {@code false} otherwise
   */
  public static boolean isTrueInHierarchy(@Nullable Component component, @NotNull Object key) {
    return isSetInHierarchy(component, key, Boolean.TRUE);
  }


  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a boolean client property
   * @return {@code true} if the property value is {@link Boolean#FALSE}, or {@code false} otherwise
   */
  public static boolean isFalse(@Nullable Component component, @NotNull Object key) {
    return isSet(component, key, Boolean.FALSE);
  }

  /**
   * @param component a Swing component that may hold a client property value
   * @param key       a key corresponding to a boolean client property
   * @return {@code true} if the property value is {@link Boolean#FALSE}, or {@code false} otherwise
   */
  public static boolean isFalseInHierarchy(@Nullable Component component, @NotNull Object key) {
    return isSetInHierarchy(component, key, Boolean.FALSE);
  }

  public static @NotNull Map<Object, Object> getAllClientProperties(Object component) {
    Map<Object, Object> map = new LinkedHashMap<>();
    if (component instanceof RootPaneContainer) component = ((RootPaneContainer)component).getRootPane();
    if (component instanceof JComponent) {
      try {
        Method method = ReflectionUtil.getDeclaredMethod(JComponent.class, "getClientProperties");
        if (method == null) {
          return Collections.emptyMap();
        }
        method.setAccessible(true);
        Object table = method.invoke(component);
        method = ReflectionUtil.getDeclaredMethod(table.getClass(), "getKeys", Object[].class);
        if (method == null) return Collections.emptyMap();
        method.setAccessible(true);
        Object arr = method.invoke(table, new Object[1]);
        if (arr instanceof Object[]) {
          for (Object key : (Object[])arr) {
            map.put(key, ((JComponent)component).getClientProperty(key));
          }
        }
        return Collections.unmodifiableMap(map);
      }
      catch (Exception ignored) {
      }
    }
    return Collections.emptyMap();
  }
}
