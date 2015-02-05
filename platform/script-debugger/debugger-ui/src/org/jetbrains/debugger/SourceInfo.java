package org.jetbrains.debugger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XSourcePositionWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SourceInfo extends XSourcePositionWrapper {
  private final String functionName;
  private final int column;

  @Nullable
  public static SourceInfo create(@Nullable String functionName, @Nullable VirtualFile file, int line, int column) {
    if (file == null) {
      return null;
    }

    XSourcePosition position;
    // binary file will be decompiled, must be under read action
    AccessToken token = file.getFileType().isBinary() ? ReadAction.start() : null;
    try {
      position = XDebuggerUtil.getInstance().createPosition(file, line);
    }
    finally {
      if (token != null) {
        token.finish();
      }
    }
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
    return new OpenFileDescriptor(project, myPosition.getFile(), myPosition.getLine(), column);
  }

  @Override
  public String toString() {
    return myPosition.getFile() + ":" + myPosition.getLine() + (column == -1 ? "": (":" + getColumn()));
  }
}
