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

  public void setWatches(String configurationName, List<XExpression> expressions) {
    if (expressions != null && expressions.size() > 0) {
      watches.put(configurationName, expressions);
    }
    else {
      watches.remove(configurationName);
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

    for (ConfigurationState expressionState : ContainerUtil.notNullize(state.getExpressions())) {
      List<WatchState> expressionStates = expressionState.getExpressionStates();
      if (!ContainerUtil.isEmpty(expressionStates)) {
        watches.put(expressionState.getName(), ContainerUtil.mapNotNull(expressionStates, XExpressionState::toXExpression));
      }
    }
  }
}
