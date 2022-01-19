// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener;

final class IgnoredFileCacheCleaner implements PersistentFsConnectionListener {
  @Override
  public void beforeConnectionClosed() {
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).clearIgnoredFileCache();
  }
}
