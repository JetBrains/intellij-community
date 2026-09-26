// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class PropertiesHelper {
  private static final @NotNull Map<String, Class<?>> TYPES = Collections.synchronizedMap(new HashMap<>());

  private final @NotNull Map<String, Object> myProperties = new HashMap<>();
  private final @NotNull PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  void setProperty(@NotNull String name, @Nullable Object value) {
    // only known properties are validated
    Class<?> type = TYPES.get(name);
    if (type != null && !type.isInstance(value)) {
       throw new IllegalArgumentException("JCEF: the property " + name + " should be " + type.getName());
    }
    synchronized (myProperties) {
      Object oldValue = myProperties.put(name, value);
      myPropertyChangeSupport.firePropertyChange(name, oldValue, value);
    }
  }

  @Nullable
  Object getProperty(@NotNull String name) {
    synchronized (myProperties) {
      return myProperties.get(name);
    }
  }

  void addPropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(name, listener);
  }

  void removePropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(name, listener);
  }

  static void setType(@NotNull String name, @NotNull Class<?> type) {
    TYPES.put(name, type);
  }

  boolean is(@NotNull String name) {
    return Boolean.TRUE.equals(myProperties.get(name));
  }

  @SuppressWarnings("SameParameterValue")
  int intValue(@NotNull String name, int defaultValue) {
    Object value = getProperty(name);
    return value instanceof Integer ? (int)value : defaultValue;
  }
}
