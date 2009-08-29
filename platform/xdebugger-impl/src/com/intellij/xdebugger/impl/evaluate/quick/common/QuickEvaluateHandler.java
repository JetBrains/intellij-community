package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author nik
 */
public abstract class QuickEvaluateHandler {

  public abstract boolean isEnabled(@NotNull Project project);

  @Nullable
  public abstract AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type);

  public abstract boolean canShowHint(@NotNull Project project);

  public abstract int getValueLookupDelay(final Project project);
}
