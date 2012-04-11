package com.intellij.coverage;

import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;

/**
 * @author traff
 */
public abstract class CoverageOptions {
  public static final ExtensionPointName<CoverageOptions> EP_NAME = ExtensionPointName.create("com.intellij.coverageOptions");

  public abstract JComponent getComponent();

  public abstract boolean isModified();

  public abstract void apply();

  public abstract void reset();

  public abstract void disposeUIResources();
}
