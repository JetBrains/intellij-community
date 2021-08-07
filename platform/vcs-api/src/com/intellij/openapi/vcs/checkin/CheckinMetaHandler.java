// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import org.jetbrains.annotations.NotNull;

/**
 * Implemented by checkin handlers that need to control the process of running other
 * checkin handlers.
 */
public interface CheckinMetaHandler {
  void runCheckinHandlers(@NotNull Runnable runnable);
}