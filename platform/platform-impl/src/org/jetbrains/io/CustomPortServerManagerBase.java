package org.jetbrains.io;

import org.jetbrains.ide.CustomPortServerManager;

public abstract class CustomPortServerManagerBase extends CustomPortServerManager {
  protected CustomPortService manager;

  @Override
  public void setManager(CustomPortService manager) {
    this.manager = manager;
  }
}