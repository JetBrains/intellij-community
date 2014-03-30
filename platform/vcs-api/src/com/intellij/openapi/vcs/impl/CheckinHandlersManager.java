/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
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
  public abstract List<BaseCheckinHandlerFactory> getRegisteredCheckinHandlerFactories(AbstractVcs<?>[] allActiveVcss);

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use EP {@link #getRegisteredCheckinHandlerFactories(AbstractVcs[])} (to remove in IDEA 14) */
  public abstract List<VcsCheckinHandlerFactory> getMatchingVcsFactories(@NotNull final List<AbstractVcs> keys);

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use EP {@link com.intellij.openapi.vcs.checkin.CheckinHandlerFactory#EP_NAME} (to remove in IDEA 14) */
  public abstract void registerCheckinHandlerFactory(BaseCheckinHandlerFactory factory);

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use EP {@link com.intellij.openapi.vcs.checkin.CheckinHandlerFactory#EP_NAME} (to remove in IDEA 14) */
  public abstract void unregisterCheckinHandlerFactory(BaseCheckinHandlerFactory factory);
}
