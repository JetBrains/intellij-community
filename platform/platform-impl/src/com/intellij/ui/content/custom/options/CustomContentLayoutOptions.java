package com.intellij.ui.content.custom.options;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CustomContentLayoutOptions {

  @NotNull
  CustomContentLayoutOption[] getAvailableOptions();

  void select(@NotNull CustomContentLayoutOption option);

  boolean isSelected(@NotNull CustomContentLayoutOption option);

  boolean isHidden();

  void restore();

  void onHide();

  @NotNull
  String getDisplayName();

  boolean isHideOptionVisible();
}
