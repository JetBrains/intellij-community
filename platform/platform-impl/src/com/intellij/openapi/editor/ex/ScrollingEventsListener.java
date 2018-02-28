package com.intellij.openapi.editor.ex;


import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ScrollingEventsListener extends EventListener {
  void scrollToCaret(@NotNull ScrollType scrollType);
  void scrollTo(@NotNull LogicalPosition pos, @NotNull ScrollType scrollType);
}
