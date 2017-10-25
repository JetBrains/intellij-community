// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class XDebuggerWatchesManager implements PersistentStateComponent<XDebuggerWatchesManager.WatchesManagerState> {
  private final Map<String, XExpression[]> watches = ContainerUtil.newConcurrentMap();

  @NotNull
  public XExpression[] getWatches(String confName) {
    XExpression[] expressions = watches.get(confName);
    if (expressions == null) {
      return new XExpression[0];
    }
    return expressions;
  }

  public void setWatches(String configurationName, XExpression[] expressions) {
    if (expressions != null && expressions.length > 0) {
      watches.put(configurationName, expressions);
    }
    else {
      watches.remove(configurationName);
    }
  }

  @Override
  public WatchesManagerState getState() {
    WatchesManagerState state = new WatchesManagerState();
    for (Map.Entry<String, XExpression[]> entry : watches.entrySet()) {
      state.expressions.add(new ConfigurationState(entry.getKey(), entry.getValue()));
    }
    return state;
  }

  @Override
  public void loadState(WatchesManagerState state) {
    watches.clear();
    if (state != null) {
      for (ConfigurationState expressionState : state.expressions) {
        WatchState[] expressionStates = expressionState.myExpressionStates;
        if (expressionStates != null) {
          watches.put(expressionState.myName, ContainerUtil.mapNotNull(expressionStates, XExpressionState::toXExpression, new XExpression[0]));
        }
      }
    }
  }

  @Tag("watches-manager")
  public static class WatchesManagerState {
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public List<ConfigurationState> expressions = new ArrayList<>();
  }

  @Tag("configuration")
  public static class ConfigurationState {
    @Attribute("name")
    public String myName;

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public WatchState[] myExpressionStates;

    public ConfigurationState() {
    }

    public ConfigurationState(String name, XExpression[] expressions) {
      myName = name;
      myExpressionStates = new WatchState[expressions.length];
      for (int i = 0; i < expressions.length; i++) {
        myExpressionStates[i] = new WatchState(expressions[i]);
      }
    }
  }
  @Tag("watch")
  public static class WatchState extends XExpressionState {
    public WatchState() {}

    public WatchState(XExpression expression) {
      super(expression);
    }
  }
}
