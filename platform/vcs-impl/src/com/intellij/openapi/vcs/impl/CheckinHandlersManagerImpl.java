/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author irengrig
 *         Date: 1/28/11
 *         Time: 5:21 PM
 */
public class CheckinHandlersManagerImpl extends CheckinHandlersManager {
  private final List<BaseCheckinHandlerFactory> myRegisteredBeforeCheckinHandlers;
  private final MultiMap<VcsKey, VcsCheckinHandlerFactory> myVcsMap;
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public CheckinHandlersManagerImpl(final Project project, final ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myVcsMap = new MultiMap<VcsKey, VcsCheckinHandlerFactory>();
    myRegisteredBeforeCheckinHandlers = new ArrayList<BaseCheckinHandlerFactory>();

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        myRegisteredBeforeCheckinHandlers
          .addAll(Arrays.asList(Extensions.<CheckinHandlerFactory>getExtensions(CheckinHandlerFactory.EP_NAME)));
        final VcsCheckinHandlerFactory[] vcsCheckinHandlerFactories = Extensions.getExtensions(VcsCheckinHandlerFactory.EP_NAME, myProject);
        for (VcsCheckinHandlerFactory factory : vcsCheckinHandlerFactories) {
          myVcsMap.putValue(factory.getKey(), factory);
        }
      }
    });
  }

  @Override
  public List<BaseCheckinHandlerFactory> getRegisteredCheckinHandlerFactories() {
    final AbstractVcs[] allActiveVcss = myVcsManager.getAllActiveVcss();
    final ArrayList<BaseCheckinHandlerFactory> list =
      new ArrayList<BaseCheckinHandlerFactory>(myRegisteredBeforeCheckinHandlers.size() + allActiveVcss.length);
    list.addAll(myRegisteredBeforeCheckinHandlers);
    for (AbstractVcs vcs : allActiveVcss) {
      final Collection<VcsCheckinHandlerFactory> factories = myVcsMap.get(vcs.getKeyInstanceMethod());
      if (factories != null && ! factories.isEmpty()) {
        list.addAll(factories);
      }
    }
    return list;
  }

  @Override
  public List<VcsCheckinHandlerFactory> getMatchingVcsFactories(@NotNull List<AbstractVcs> vcsList) {
    final SmartList<VcsCheckinHandlerFactory> result = new SmartList<VcsCheckinHandlerFactory>();
    for (AbstractVcs vcs : vcsList) {
      final Collection<VcsCheckinHandlerFactory> factories = myVcsMap.get(vcs.getKeyInstanceMethod());
      if (factories != null && ! factories.isEmpty()) {
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
