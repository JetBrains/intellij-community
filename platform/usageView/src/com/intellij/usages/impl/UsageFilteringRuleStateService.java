// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Set;

@Internal
@Service
@State(name = "UsageFilteringRuleState", storages = @Storage("usageView.xml"))
public final class UsageFilteringRuleStateService implements PersistentStateComponent<String[]> {

  private final Set<String> myState = new HashSet<>();

  @Override
  public synchronized String[] getState() {
    return ArrayUtil.toStringArray(myState);
  }

  @Override
  public synchronized void loadState(@NotNull String @NotNull [] state) {
    myState.clear();
    ContainerUtil.addAll(myState, state);
  }

  public static @NotNull UsageFilteringRuleStateService getInstance() {
    return ApplicationManager.getApplication().getService(UsageFilteringRuleStateService.class);
  }

  public static @NotNull UsageFilteringRuleState createFilteringRuleState() {
    return new UsageFilteringRuleStateImpl(getInstance().myState);
  }

  @TestOnly
  public static @NotNull AccessToken withRule(@NotNull String ruleId, boolean active) {
    UsageFilteringRuleStateService instance = getInstance();
    Set<String> state = instance.myState;
    boolean modified = active ? state.add(ruleId) : state.remove(ruleId);
    if (!modified) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    else {
      return new AccessToken() {
        @Override
        public void finish() {
          boolean modifiedBack = active ? state.remove(ruleId) : state.add(ruleId);
          assert modifiedBack;
        }
      };
    }
  }
}
