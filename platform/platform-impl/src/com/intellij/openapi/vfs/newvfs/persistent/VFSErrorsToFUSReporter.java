// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;

/**
 * On app exit report accumulated VFS errors to FUS
 */
final class VFSErrorsToFUSReporter implements AppLifecycleListener {
  VFSErrorsToFUSReporter() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void appWillBeClosed(boolean isRestart) {
    FSRecordsImpl fsRecords = FSRecords.getInstanceIfCreatedAndNotDisposed();
    if (fsRecords == null) {
      return;
    }

    long sessionDurationMs = System.currentTimeMillis() - ApplicationManager.getApplication().getStartTime();

    VfsUsageCollector.logVfsInternalErrors(
      fsRecords.getCreationTimestamp(),
      sessionDurationMs,
      fsRecords.corruptionsDetected()
    );
  }
}
