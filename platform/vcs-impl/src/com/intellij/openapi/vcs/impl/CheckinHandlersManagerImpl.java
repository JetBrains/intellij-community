/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CheckinHandlersManagerImpl extends CheckinHandlersManager {
  private final List<BaseCheckinHandlerFactory> myRegisteredBeforeCheckinHandlers;
  private final MultiMap<VcsKey, VcsCheckinHandlerFactory> myVcsMap;

  public CheckinHandlersManagerImpl() {
    myVcsMap = new MultiMap<>();
    myRegisteredBeforeCheckinHandlers = new ArrayList<>();
    ContainerUtil.addAll(myRegisteredBeforeCheckinHandlers, CheckinHandlerFactory.EP_NAME.getExtensions());
    for (VcsCheckinHandlerFactory factory : VcsCheckinHandlerFactory.EP_NAME.getExtensions()) {
      myVcsMap.putValue(factory.getKey(), factory);
    }
  }

  @Override
  public List<BaseCheckinHandlerFactory> getRegisteredCheckinHandlerFactories(AbstractVcs<?>[] allActiveVcss) {
    final List<BaseCheckinHandlerFactory> list =
      new ArrayList<>(myRegisteredBeforeCheckinHandlers.size() + allActiveVcss.length);
    list.addAll(myRegisteredBeforeCheckinHandlers);
    for (AbstractVcs vcs : allActiveVcss) {
      final Collection<VcsCheckinHandlerFactory> factories = myVcsMap.get(vcs.getKeyInstanceMethod());
      if (!factories.isEmpty()) {
        list.addAll(factories);
      }
    }
    return list;
  }
}
