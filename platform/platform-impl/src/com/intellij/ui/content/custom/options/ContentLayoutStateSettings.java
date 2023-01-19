package com.intellij.ui.content.custom.options;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface ContentLayoutStateSettings {
  boolean isSelected();

  void setSelected(boolean state);

  @NotNull @Nls String getDisplayName();

  void restore();

  boolean isEnabled();
}
