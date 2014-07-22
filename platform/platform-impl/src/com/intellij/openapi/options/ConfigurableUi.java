package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ConfigurableUi<S> {
  void reset(@NotNull S settings);

  boolean isModified(@NotNull S settings);

  void apply(@NotNull S settings);

  @NotNull
  JComponent getComponent();
}