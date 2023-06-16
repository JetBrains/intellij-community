// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.Objects;

public interface VcsLogUiProperties {
  @RequiresEdt
  @NotNull <T> T get(@NotNull VcsLogUiProperty<T> property);

  @RequiresEdt
  <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value);

  <T> boolean exists(@NotNull VcsLogUiProperty<T> property);

  @RequiresEdt
  void addChangeListener(@NotNull PropertiesChangeListener listener);

  @RequiresEdt
  void addChangeListener(@NotNull PropertiesChangeListener listener, @NotNull Disposable parent);

  @RequiresEdt
  void removeChangeListener(@NotNull PropertiesChangeListener listener);

  class VcsLogUiProperty<T> {
    private final @NotNull String myName;

    public VcsLogUiProperty(@NonNls @NotNull String name) {
      myName = name;
    }

    public @NotNull String getName() {
      return myName;
    }

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VcsLogUiProperty<?> property = (VcsLogUiProperty<?>)o;
      return Objects.equals(myName, property.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }
  }

  interface PropertiesChangeListener extends EventListener {
    <T> void onPropertyChanged(@NotNull VcsLogUiProperty<T> property);
  }
}
