package com.intellij.xdebugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

public abstract class XSourcePositionWrapper implements XSourcePosition {
  protected final XSourcePosition myPosition;

  protected XSourcePositionWrapper(@NotNull XSourcePosition position) {
    myPosition = position;
  }

  @Override
  public final int getLine() {
    return myPosition.getLine();
  }

  @Override
  public final int getOffset() {
    return myPosition.getOffset();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myPosition.getFile();
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project) {
    return myPosition.createNavigatable(project);
  }
}