package com.intellij.ui.content.custom.options;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CustomContentLayoutOptions {

  Key<CustomContentLayoutOptions> KEY = Key.create("CUSTOM_LAYOUT_OPTIONS");

  @NotNull
  CustomContentLayoutOption[] getAvailableOptions();

  void select(@NotNull CustomContentLayoutOption option);

  boolean isSelected(@NotNull CustomContentLayoutOption option);

  void restore();

  void onHide();
}
