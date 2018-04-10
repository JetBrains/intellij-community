/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class XDebuggerWatchesManager {
  private final Map<String, List<XExpression>> watches = ContainerUtil.newConcurrentMap();

  @NotNull
  public List<XExpression> getWatches(String confName) {
    return ContainerUtil.notNullize(watches.get(confName));
  }

  public void setWatches(@NotNull String configurationName, @NotNull List<XExpression> expressions) {
    if (expressions.isEmpty()) {
      watches.remove(configurationName);
    }
    else {
      watches.put(configurationName, expressions);
    }
  }

  @NotNull
  public WatchesManagerState saveState(@NotNull WatchesManagerState state) {
    List<ConfigurationState> expressions = new SmartList<>();
    for (Map.Entry<String, List<XExpression>> entry : watches.entrySet()) {
      expressions.add(new ConfigurationState(entry.getKey(), entry.getValue()));
    }

    state.setExpressions(expressions);
    return state;
  }

  public void clearContext() {
    watches.clear();
  }

  public void loadState(@NotNull WatchesManagerState state) {
    clearContext();

    for (ConfigurationState expressionState : state.getExpressions()) {
      List<WatchState> expressionStates = expressionState.getExpressionStates();
      if (!ContainerUtil.isEmpty(expressionStates)) {
        watches.put(expressionState.getName(), ContainerUtil.mapNotNull(expressionStates, XExpressionState::toXExpression));
      }
    }
  }
}
