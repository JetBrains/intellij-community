package com.intellij.ui.content.custom.options;

import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CustomContentLayoutOption {
  boolean isEnabled();
  boolean isSelected();
  void select();
  @NotNull @Nls @NlsActions.ActionText String getDisplayName();
}
