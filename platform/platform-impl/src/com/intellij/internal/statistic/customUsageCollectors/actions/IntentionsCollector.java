// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "IntentionsCollector",
  storages = @Storage(value = "statistics.intentions.xml", roamingType = RoamingType.DISABLED)
)
public class IntentionsCollector implements PersistentStateComponent<IntentionsCollector.State> {
  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  private static final List<String> PREFIXES_TO_STRIP = Arrays.asList("com.intellij.codeInsight.",
                                                                      "com.intellij.");
  public void record(@NotNull IntentionAction action, @NotNull Language language) {
    State state = getState();
    if (state == null) return;

    String id = getIntentionId(action);

    String key = language.getID() + " " + id;
    final Integer count = state.myIntentions.get(key);
    int value = count == null ? 1 : count + 1;
    state.myIntentions.put(key, value);
  }

  @NotNull
  private static String getIntentionId(@NotNull IntentionAction action) {
    Object handler = action;
    if (action instanceof IntentionActionDelegate) {
      IntentionAction delegate = ((IntentionActionDelegate)action).getDelegate();
      if (delegate != action) {
        return getIntentionId(delegate);
      }
    } else if (action instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)action).getFix();
      if (fix != action) {
        handler = fix;
      }
    }

    String fqn = handler.getClass().getName();
    for (String prefix : PREFIXES_TO_STRIP) {
      fqn = StringUtil.trimStart(fqn, prefix);
    }

    if (UsagesCollector.isNotBundledPluginClass(handler.getClass())) {
      fqn = "[!]" + fqn;
    }

    return fqn;
  }

  public static IntentionsCollector getInstance() {
    return ServiceManager.getService(IntentionsCollector.class);
  }

  final static class State {
    @Tag("Intentions")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "name", valueAttributeName = "count")
    public Map<String, Integer> myIntentions = new HashMap<>();
  }

  final static class IntentionUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create("Intentions");

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      State state = getInstance().getState();
      assert state != null;
      return ContainerUtil.map2Set(state.myIntentions.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
}

