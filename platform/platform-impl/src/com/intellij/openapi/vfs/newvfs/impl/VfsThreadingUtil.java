// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.InternalThreading;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
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
      Application application = ApplicationManager.getApplication();
      if (application instanceof ApplicationImpl) {
        try {
          InternalThreading.invokeAndWaitWithTransferredWriteAction(action);
        }
        catch (Throwable t) {
          throw new RuntimeException(t);
        }
      } else {
        LOG.warn("Cannot invoke EDT VFS listeners on background");
      }
    }
  }
}
