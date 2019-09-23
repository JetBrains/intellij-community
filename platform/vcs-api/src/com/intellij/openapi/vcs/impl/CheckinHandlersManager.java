// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CheckinHandlersManager {
  public static CheckinHandlersManager getInstance() {
    return ServiceManager.getService(CheckinHandlersManager.class);
  }

  /**
   * Returns the list of all registered factories which provide callbacks to run before and after
   * VCS checkin operations.
   *
   * @return the list of registered factories
   */
  @NotNull
  public abstract List<BaseCheckinHandlerFactory> getRegisteredCheckinHandlerFactories(@NotNull AbstractVcs[] vcses);
}
