// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.TransferredWriteActionService;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class VfsThreadingUtil {
  private VfsThreadingUtil() { }
  private static final Logger LOG = Logger.getInstance(VfsThreadingUtil.class);


  public static void runActionOnEdtRegardlessOfCurrentThread(Runnable action) {
    if (EDT.isCurrentThreadEdt()) {
      action.run();
    }
    else {
      ApplicationManager.getApplication().getService(TransferredWriteActionService.class).runOnEdtWithTransferredWriteActionAndWait(action);
    }
  }
}
