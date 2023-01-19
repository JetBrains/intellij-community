// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Prefer using {@link CheckinModificationHandler} with {@link CheckinHandler#beforeCheckin} or {@link CommitCheck}.
 */
@Deprecated
public interface CheckinMetaHandler extends CheckinModificationHandler {
  /**
   * Implementations MUST call the callback or throw an exception.
   */
  void runCheckinHandlers(@NotNull Runnable runnable);
}