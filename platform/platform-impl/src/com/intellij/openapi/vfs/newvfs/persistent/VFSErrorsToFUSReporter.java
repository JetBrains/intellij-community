// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import org.jetbrains.annotations.ApiStatus;

/**
 * On app exit report accumulated VFS errors to FUS
 */
@ApiStatus.Internal
public class VFSErrorsToFUSReporter implements AppLifecycleListener {
  @Override
  public void appWillBeClosed(boolean isRestart) {
    FSRecordsImpl impl = FSRecords.implOrFail();
    long sessionDurationMs = System.currentTimeMillis() - ApplicationManager.getApplication().getStartTime();

    VfsUsageCollector.logVfsInternalErrors(
      impl.getCreationTimestamp(),
      sessionDurationMs,
      impl.corruptionsDetected()
    );
  }
}
