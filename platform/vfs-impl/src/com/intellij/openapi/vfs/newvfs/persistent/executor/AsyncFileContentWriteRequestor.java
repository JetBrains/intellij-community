// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.executor;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface for requestors in VFS that opt in to asynchronous file content writes.
 *
 * @see com.intellij.openapi.vfs.newvfs.FileSystemInterface#getOutputStream(VirtualFile, Object, long, long)
 */
@ApiStatus.Internal
public interface AsyncFileContentWriteRequestor { }
