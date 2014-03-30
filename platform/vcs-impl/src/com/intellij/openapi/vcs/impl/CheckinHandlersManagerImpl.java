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

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CheckinHandlersManagerImpl extends CheckinHandlersManager {
  private final List<BaseCheckinHandlerFactory> myRegisteredBeforeCheckinHandlers;
  private final MultiMap<VcsKey, VcsCheckinHandlerFactory> myVcsMap;

  public CheckinHandlersManagerImpl() {
    myVcsMap = new MultiMap<VcsKey, VcsCheckinHandlerFactory>();
    myRegisteredBeforeCheckinHandlers = new ArrayList<BaseCheckinHandlerFactory>();
    ContainerUtil.addAll(myRegisteredBeforeCheckinHandlers, CheckinHandlerFactory.EP_NAME.getExtensions());
    for (VcsCheckinHandlerFactory factory : VcsCheckinHandlerFactory.EP_NAME.getExtensions()) {
      myVcsMap.putValue(factory.getKey(), factory);
    }
  }

  @Override
  public List<BaseCheckinHandlerFactory> getRegisteredCheckinHandlerFactories(AbstractVcs<?>[] allActiveVcss) {
    final List<BaseCheckinHandlerFactory> list =
      new ArrayList<BaseCheckinHandlerFactory>(myRegisteredBeforeCheckinHandlers.size() + allActiveVcss.length);
    list.addAll(myRegisteredBeforeCheckinHandlers);
    for (AbstractVcs vcs : allActiveVcss) {
      final Collection<VcsCheckinHandlerFactory> factories = myVcsMap.get(vcs.getKeyInstanceMethod());
      if (!factories.isEmpty()) {
        list.addAll(factories);
      }
    }
    return list;
  }

  @Override
  public List<VcsCheckinHandlerFactory> getMatchingVcsFactories(@NotNull List<AbstractVcs> vcsList) {
    final List<VcsCheckinHandlerFactory> result = new SmartList<VcsCheckinHandlerFactory>();
    for (AbstractVcs vcs : vcsList) {
      final Collection<VcsCheckinHandlerFactory> factories = myVcsMap.get(vcs.getKeyInstanceMethod());
      if (!factories.isEmpty()) {
        result.addAll(factories);
      }
    }
    return result;
  }

  @Override
  public void registerCheckinHandlerFactory(BaseCheckinHandlerFactory factory) {
    myRegisteredBeforeCheckinHandlers.add(factory);
  }

  @Override
  public void unregisterCheckinHandlerFactory(BaseCheckinHandlerFactory handler) {
    myRegisteredBeforeCheckinHandlers.remove(handler);
  }
}
