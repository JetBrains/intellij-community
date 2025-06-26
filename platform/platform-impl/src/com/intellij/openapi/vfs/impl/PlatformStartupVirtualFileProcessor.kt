// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.vfs.StartupVirtualFileProcessor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.fileEditor.FileEntry

private class PlatformStartupVirtualFileProcessor : StartupVirtualFileProcessor {
  override fun processVirtualFileOnStartup(file: VirtualFile, fileEntry: FileEntry) {
    // do nothing for local mode and RD server
  }
}