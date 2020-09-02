// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import com.intellij.xdebugger.impl.inline.InlineWatch;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class XDebuggerWatchesManager {
  private final Map<String, List<XExpression>> watches = new ConcurrentHashMap<>();
  private final Map<String, List<InlineWatch>> inlineWatches = new ConcurrentHashMap<>();

  public @NotNull List<XExpression> getWatches(String confName) {
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

  public void setInlineWatches(@NotNull String configurationName, @NotNull List<InlineWatch> expressions) {
    if (expressions.isEmpty()) {
      inlineWatches.remove(configurationName);
    }
    else {
      inlineWatches.put(configurationName, expressions);
    }
  }

  public List<InlineWatch> getInlineWatches(String confName) {
    return ContainerUtil.notNullize(inlineWatches.get(confName));
  }

  public @NotNull WatchesManagerState saveState(@NotNull WatchesManagerState state) {
    List<ConfigurationState> expressions = state.getExpressions();
    expressions.clear();
    Stream.concat(watches.keySet().stream(), inlineWatches.keySet().stream()).distinct().forEach(
      configKey -> {
        expressions.add(new ConfigurationState(configKey, watches.get(configKey), inlineWatches.get(configKey)));
      }
    );
    return state;
  }

  public void clearContext() {
    watches.clear();
    inlineWatches.clear();
  }

  public void loadState(@NotNull WatchesManagerState state) {
    clearContext();

    for (ConfigurationState configurationState : state.getExpressions()) {
      List<WatchState> expressionStates = configurationState.getExpressionStates();
      if (!ContainerUtil.isEmpty(expressionStates)) {
        watches.put(configurationState.getName(), ContainerUtil.mapNotNull(expressionStates, XExpressionState::toXExpression));
      }

      List<InlineWatchState> inlineExpressionStates = configurationState.getInlineExpressionStates();
      if(!ContainerUtil.isEmpty(inlineExpressionStates)) {
        inlineWatches.put(configurationState.getName(),
                          ContainerUtil.mapNotNull(inlineExpressionStates, st -> new InlineWatch(st)));
      }
    }
  }
}
