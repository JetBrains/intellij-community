
package com.intellij.openapi.vfs.ex;

import com.intellij.openapi.vfs.VirtualFileManagerListener;

public abstract class VirtualFileManagerAdapter implements VirtualFileManagerListener {
  public void beforeRefreshStart(boolean asynchonous) {
  }

  public void afterRefreshFinish(boolean asynchonous) {
  }
}