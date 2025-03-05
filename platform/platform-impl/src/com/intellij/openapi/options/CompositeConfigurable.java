// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class CompositeConfigurable<T extends UnnamedConfigurable> extends BaseConfigurable {
  private List<T> myConfigurables;

  @Override
  public void reset() {
    for (T configurable : getConfigurables()) {
      configurable.reset();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    for (T configurable : getConfigurables()) {
      configurable.apply();
    }
  }

  @Override
  public boolean isModified() {
    for (T configurable : getConfigurables()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void disposeUIResources() {
    if (myConfigurables != null) {
      for (final T myConfigurable : myConfigurables) {
        myConfigurable.disposeUIResources();
      }
      myConfigurables = null;
    }
  }

  protected abstract @Unmodifiable @NotNull List<T> createConfigurables();

  public @Unmodifiable @NotNull List<T> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
