package com.intellij.ui;

/**
 * Base class for editor feature configurations used by {@link EditorCustomization}
 */
public abstract class EditorFeature {
  private boolean myEnabled;

  public EditorFeature(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isEnabled() {
    return myEnabled;
  }
}
