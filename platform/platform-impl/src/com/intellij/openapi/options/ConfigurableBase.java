package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ConfigurableBase<UI extends ConfigurableUi<S>, S> implements SearchableConfigurable, Configurable.NoScroll {
  private UI ui;

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  protected abstract S getSettings();

  @Override
  public void reset() {
    if (ui != null) {
      ui.reset(getSettings());
    }
  }

  @Nullable
  @Override
  public final JComponent createComponent() {
    if (ui == null) {
      ui = createUi();
    }
    return ui.getComponent();
  }

  protected abstract UI createUi();

  @Override
  public final boolean isModified() {
    return ui != null && ui.isModified(getSettings());
  }

  @Override
  public final void apply() {
    if (ui != null) {
      ui.apply(getSettings());
    }
  }

  @Override
  public void disposeUIResources() {
    ui = null;
  }
}