// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.ex;

import com.intellij.openapi.vfs.VirtualFileManagerListener;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link VirtualFileManagerListener} directly.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public abstract class VirtualFileManagerAdapter implements VirtualFileManagerListener {
  @Override
  public void beforeRefreshStart(boolean asynchronous) {
  }

  @Override
  public void afterRefreshFinish(boolean asynchronous) {
  }
}