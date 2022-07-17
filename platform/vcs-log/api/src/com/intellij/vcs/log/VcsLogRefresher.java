// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Tells the VCS Log, that some data has possibly become obsolete and needs to be refreshed.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogRefresher {

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i.e. it can query VCS just for the part of the log.
   */
  void refresh(@NotNull VirtualFile root);
}
