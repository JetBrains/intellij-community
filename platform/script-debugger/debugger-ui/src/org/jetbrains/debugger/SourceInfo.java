package org.jetbrains.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XSourcePositionWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceInfo extends XSourcePositionWrapper {
  private final String functionName;
  private final int column;

  @Nullable
  public static SourceInfo create(@Nullable String functionName, @Nullable XSourcePosition position, int column) {
    return position == null ? null : new SourceInfo(functionName, position, column);
  }

  private SourceInfo(@Nullable String functionName, @NotNull XSourcePosition position, int column) {
    super(position);

    this.functionName = functionName;
    this.column = column;
  }

  @Nullable
  public String getFunctionName() {
    return functionName;
  }

  public int getColumn() {
    return column;
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project) {
    return myPosition.createNavigatable(project);
  }
}
