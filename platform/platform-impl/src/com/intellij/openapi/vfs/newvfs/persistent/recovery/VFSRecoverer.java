// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSLoader;
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Recoverer is an algorithm to recover from some problems met during VFS loading.
 * <p/>
 * Recover must inspect {@link PersistentFSLoader#problemsDuringLoad}, find problems
 * which it could (potentially) deal with, and try to fix them.
 * <p/>
 * If the recovery attempt is successful -> recoverer must use {@link PersistentFSLoader#problemsWereRecovered(List)}
 * to mark fixed problems as 'recovered'.
 * <p/>
 * If the recovery attempt is unsuccessful -> recoverer could either leave problems as-is,
 * or use {@link PersistentFSLoader#problemsRecoveryFailed(List, VFSInitException.ErrorCategory, String)}
 * to better describe what exactly has prevented recovery.
 * <p/>
 */
public interface VFSRecoverer {
  void tryRecover(@NotNull PersistentFSLoader loader);
}
