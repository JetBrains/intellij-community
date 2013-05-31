package org.jetbrains.io;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.CustomPortServerManager;

public abstract class CustomPortServerManagerBase extends CustomPortServerManager {
  @Nullable
  protected CustomPortService manager;

  @Override
  public void setManager(@Nullable CustomPortService manager) {
    this.manager = manager;
  }
}